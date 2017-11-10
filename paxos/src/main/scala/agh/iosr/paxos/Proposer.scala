package agh.iosr.paxos

import java.util
import java.util.concurrent.TimeUnit

import agh.iosr.paxos.Messages._
import agh.iosr.paxos.predef._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}

import scala.collection._
import scala.concurrent.duration.FiniteDuration

/*
Checklist:
* duplicate messages
* retransmission
 */


object Proposer {
  def props(learner: ActorRef, nodeId: NodeId, nodeCount: NodeId, logger: ActorRef = null): Props =
    Props(new Proposer(learner, nodeId, nodeCount, logger))

  case class RPromise(lastRoundVoted: RoundId, ov: Option[KeyValue])

  // @todo put this shit in companion object
  sealed trait PaxosInstanceState {
    def mo: RoundIdentifier
  }

  object PaxosInstanceState {
    def unapply(pis: PaxosInstanceState): Option[RoundIdentifier] = Some(pis.mo)
  }

  type PromiseMap = mutable.Map[NodeId, RPromise]

  case class Phase1(mo: RoundIdentifier,
                    ourValue: KeyValue,
                    rejectors: mutable.Set[NodeId] = mutable.Set(),
                    promises: PromiseMap = mutable.Map[NodeId, RPromise]()) extends PaxosInstanceState

  case class Phase2(mo: RoundIdentifier,
                    votedValue: KeyValue,
                    ourValue: KeyValue,
                    nacks: mutable.Set[NodeId] = mutable.Set()) extends PaxosInstanceState


  case object Start

  case object P1Tick
  case object P2Tick
  case object Timeout

  case class TimerConf(key: String, msInterval: Int, msg: Any)
  private val p1Conf = TimerConf("p1a", 200, P1Tick)
  private val p2Conf = TimerConf("p2a", 200, P2Tick)
  private val tConf = TimerConf("timeout", 2000, Timeout)

  trait LogMessage
  case class ContextChange(to: String) extends LogMessage
  case class PromiseDuplicate(cm: ConsensusMessage) extends LogMessage
  case class RequestReceived(req: KeyValue) extends LogMessage
  case class RequestQueued(req: KeyValue) extends LogMessage
  case class PrepareSent(ri: RoundIdentifier) extends LogMessage
  case class RestartingInstance(req: KeyValue) extends LogMessage
  case class InitiatingVoting(ri: RoundIdentifier, req: KeyValue) extends LogMessage
  case class IgnoringInstance(ignored: InstanceId, current: InstanceId) extends LogMessage
  case class IgnoringRound(instance: InstanceId, ignored: RoundId, current: RoundId) extends LogMessage
  case class InstanceSuccessful(instance: InstanceId) extends LogMessage
  case class InstanceChoseWrongValue(instance: InstanceId, value: KeyValue) extends LogMessage
}

class Proposer(val learner: ActorRef, val nodeId: NodeId, val nodeCount: NodeId, val logger: ActorRef)
  extends Actor with ActorLogging with Timers {

  import Proposer._

  private val minQuorumSize = nodeCount/2 + 1

  private var communicator: ActorRef = _
  private var mostRecentlySeenInstanceId: InstanceId = 0
  private val idGen = new IdGenerator(nodeId)

  private var paxosState: Option[PaxosInstanceState] = None
  private def state[T] = paxosState.get.asInstanceOf[T]

  private val rqQueue = new util.LinkedList[KeyValue]

  override def preStart(): Unit = {
    super.preStart()

    // @todo learner doesn't send anything back, so this might be susceptible to races
    learner ! LearnerSubscribe
  }

  override def receive = {
    case Ready =>
      communicator = sender()
      context.become(idle)
  }

  def idle: Receive = {
    case KvsSend(key, value) =>
      rqQueue.add(KeyValue(key, value))
      logg(RequestReceived(KeyValue(key, value)))
      context.become(phase1)
      logg(ContextChange("phase1"))
      self ! Start
  }

  def phase1: Receive = {
    case KvsSend(key, value) =>
      rqQueue.addLast(KeyValue(key, value))
      logg(RequestQueued(KeyValue(key, value)))

    case Start if rqQueue.size() > 0 =>
      val v = rqQueue.pop()

      val iid = mostRecentlySeenInstanceId + 1
      val rid = idGen.nextId()
      val mo = RoundIdentifier(iid, rid)

      communicator ! SendMulticast(Prepare(mo))
      logg(PrepareSent(mo))
      startTimer(p1Conf)

      mostRecentlySeenInstanceId += 1
      paxosState = Some(Phase1(mo, v))

    case ReceivedMessage(m @ ConsensusMessage(messageMo), sid) =>
      val Some(PaxosInstanceState(currentMo)) = paxosState

      if(messageMo == currentMo) {
        val st = state[Phase1]

        m match {
          case RoundTooOld(_, mostRecent) =>
            // @todo when we gain possibility of ID correction, modify it here
            // someone is already using this instance - we need to switch to new one
            // we didn't sent 2a yet, so we can simply abandon this instance and try for a new one

            // just for comletness (in case mechanism changes later)
            st.rejectors += sid

            paxosState = None
            stopTimer(p1Conf)
            rqQueue.addFirst(st.ourValue)
            logg(RestartingInstance(st.ourValue))
            self ! Start

          case Promise(_, vr, ovv) =>
            if (st.promises.contains(sid)) {
              log.info(s"Already got promise from $sid, must be a duplicate: $m")
              logg(PromiseDuplicate(m))
            } else {
              st.promises += (sid -> RPromise(vr, ovv))

              if (st.promises.size >= minQuorumSize) {
                stopTimer(p1Conf)

                val v = pickValueToVote(st.promises, currentMo.roundId).getOrElse(st.ourValue)

                communicator ! SendMulticast(AcceptRequest(currentMo, v))
                logg(InitiatingVoting(currentMo, v))

                // enter Phase 2
                paxosState = Some(Phase2(currentMo, v, st.ourValue))
                context.become(phase2)
                logg(ContextChange("phase2"))
                startTimer(p2Conf)
                startTimer(tConf)
              }
            }
        }

      } else {
        if (messageMo.instanceId != currentMo.instanceId) {
          log.info(s"Got message from instance (${messageMo.instanceId}) != current (${currentMo.instanceId}), " +
            s"ignoring: $m")
          logg(IgnoringInstance(messageMo.instanceId, currentMo.instanceId))
        } else {
          if (messageMo.roundId > currentMo.roundId) {
            log.info(s"Higher round noticed, but maybe our proposal was chosen before?")
            logg(IgnoringRound(messageMo.instanceId, messageMo.roundId, currentMo.roundId))
          } else if (messageMo.roundId < currentMo.roundId) {
            log.info(s"Got message from same instance (${messageMo.instanceId}), but lower round (${messageMo.roundId}, " +
              s"current: ${currentMo.roundId}), ignoring: $m")
            logg(IgnoringRound(messageMo.instanceId, messageMo.roundId, currentMo.roundId))
          }
        }
    }

    case P1Tick =>
      val st = state[Phase1]
      val alive = st.promises.keySet ++ st.rejectors

      (0 until nodeCount).filter(!alive.contains(_)).foreach(id => {
        // @todo helper method for sending (avoid code duplication)
        val msg = Prepare(st.mo)
        communicator ! SendUnicast(msg, id)
      })
  }

  def phase2: Receive = {
    // we wait for results of round we initiated and we have to make a decision - do we restart or value was voted for
    case KvsSend(key, value) =>
      rqQueue.addLast(KeyValue(key, value))
      logg(RequestQueued(KeyValue(key, value)))

    case ValueLearned(iid, k, v) =>
      val votedValue = KeyValue(k,v)
      val st = state[Phase2]
      if (st.mo.instanceId == iid) {
        // OK, results of our round are published
        if (votedValue == st.ourValue) {
          logg(InstanceSuccessful(iid))
          // mission accomplished, we can go back to idle
          paxosState = None
          context.become(idle)
          logg(ContextChange("idle"))
          // @todo we could also inform user that we are ready for his next request
        } else {
          logg(InstanceChoseWrongValue(iid, votedValue))
          /*
          we have to try once again
          but we might have a problem - if we were trying to complete prevously initiated round, what
          value should we try to propose in a new round? the one we were trying to push or the one we originally
          wanted?

          currently, the old one is simply _dropped_
           */

          paxosState = None
          rqQueue.addFirst(st.ourValue)
          context.become(phase1)
          logg(ContextChange("phase1"))
          self ! Start
        }

        stopTimer(p2Conf)
        stopTimer(tConf)
      }

    case ReceivedMessage(HigherProposalReceived(mmo, higherId), sid) if mmo == paxosState.get.mo =>
      // higher proposal appeared while our has been voted on -> but we cannot back off now
      // but what if our proposal has actually been accepted? we probably need to wait for the result, otherwise
      // we might overwrite some values
      // all in all, we cannot do much here
      state[Phase2].nacks += sid
      ()

    case P2Tick =>
      val cst = state[Phase2]
      val msg = AcceptRequest(cst.mo, cst.votedValue)
      (0 until nodeCount).filter(cst.nacks.contains).foreach(id => {
        communicator ! SendUnicast(msg, id)
      })

    case Timeout =>
      /* timeout, we give up */
      log.info(s"Timeout reached, aborting: ${paxosState.get.mo}")

      paxosState = None
      context.become(idle)
      logg(ContextChange("idle"))
      // @todo: good place to inform client we are free

  }

  // @todo handling of errant messages in all states

  // @todo: extract and test separatelly
  private def pickValueToVote(pm: PromiseMap, currentRid: RoundId): Option[KeyValue] = {
    val largest = mutable.Set[KeyValue]()

    var largestFound: RoundId = NULL_ROUND
    pm.values.foreach {
      case RPromise(rid, Some(v)) =>
        if (rid > largestFound) {
          largest.clear()
          largest += v
          largestFound = rid
        } else if (rid == largestFound) {
          largest += v
        }
      case RPromise(NULL_ROUND, None) => ()
      case other =>
        log.error(s"Got some illegal RPromise: $other")
    }

    if (largestFound > currentRid) {
      log.error(s"Stumbled upon something illegal while sifting through received proposals /2A, " +
        s"rid ($largestFound) > currentRid ($currentRid)/")
    }

    if (largest.size > 1) {
      log.error(s"Quorum reported in 1B more than one value: $largest")
    }

    largest.headOption
  }

  def startTimer(conf: TimerConf) = {
    timers.startPeriodicTimer(conf.key, conf.msg, FiniteDuration(conf.msInterval, TimeUnit.MILLISECONDS))
  }

  def stopTimer(conf: TimerConf) = {
    timers.cancel(conf.key)
  }

  def logg(msg: LogMessage): Unit = {
    if (logger != null)
      logger ! msg
  }

}
