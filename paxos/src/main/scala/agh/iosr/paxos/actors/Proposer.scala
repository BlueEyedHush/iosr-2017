package agh.iosr.paxos.actors

import java.util
import java.util.concurrent.TimeUnit

import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.messages.SendableMessage
import agh.iosr.paxos.predef._
import agh.iosr.paxos.utils._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.event.LoggingAdapter

import scala.collection._
import scala.concurrent.duration.FiniteDuration

object Proposer {
  def props(learner: ActorRef, nodeId: NodeId, nodeCount: NodeId, loggers: Set[ActorRef] = Set(), disableTimeouts: Boolean = false): Props =
    Props(new Proposer(learner, nodeId, nodeCount, loggers, disableTimeouts))

  case class RPromise(lastRoundVoted: RoundId, ov: Option[KeyValue])

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

  private val p1Conf = TimerConf("p1a", 200, P1Tick)
  private val p2Conf = TimerConf("p2a", 200, P2Tick)
  private val tConf = TimerConf("timeout", 2000, Timeout)

  trait Result
  case class OurValueChosen(iid: InstanceId, v: KeyValue) extends Result
  case class OverrodeInP1(iid: InstanceId, overridingRoundId: RoundId) extends Result
  case class OverrodeInP2(iid: InstanceId, overridingRoundId: RoundId) extends Result
  case class InstanceTimeout(iid: InstanceId) extends Result
}

// @todo move messages here
// @todo add type to SendableMessage, modify all case expressions

object ExecutionTracing {
  object TimeoutType extends Enumeration {
    val p1b, p2b, instance = Value
  }


  trait LogMessage
  case class CommInitialized(comm: ActorRef) extends LogMessage
  case class ContextChange(to: String) extends LogMessage
  case class NewPromise(sender: NodeId, cm: Promise) extends LogMessage
  case class PromiseDuplicate(sender: NodeId, cm: Promise) extends LogMessage
  case class RequestProcessingStarted(req: KeyValue) extends LogMessage
  case class RequestQueued(req: KeyValue, phase: String = "") extends LogMessage
  case class PrepareSent(ri: RoundIdentifier, ourV: KeyValue) extends LogMessage
  case class RoundOverridden(ri: RoundIdentifier, higherId: RoundId, phase: String) extends LogMessage
  case class InitiatingVoting(ri: RoundIdentifier, proposal: KeyValue, ourV: KeyValue) extends LogMessage
  case class IgnoringInstance(ignored: InstanceId, current: InstanceId, m: SendableMessage) extends LogMessage
  case class IgnoringRound(instance: InstanceId, ignored: RoundId, current: RoundId, m: SendableMessage) extends LogMessage
  case class InstanceSuccessful(instance: InstanceId) extends LogMessage
  case class VotingUnsuccessful(instance: InstanceId, value: KeyValue) extends LogMessage
  case class TimeoutHit(which: TimeoutType.Value, comment: String = "") extends LogMessage
  case class QueueEmpty() extends LogMessage
}

// @todo move to separate file
object Printer {
  def props(nodeId: NodeId): Props = Props(new Printer(nodeId))
}

class Printer(val nodeId: NodeId) extends Actor with ActorLogging {
  private implicit val implId: NodeId = nodeId
  private implicit val implLog: LoggingAdapter = log

  override def receive: Receive = {
    case m => FileLog.info(m)
  }
}


// @todo remove params: learner, add: Elector ref, communicator ref, RoundIdentifier
class Proposer(val learner: ActorRef, val nodeId: NodeId, val nodeCount: NodeId, val loggers: Set[ActorRef], val disableTimeouts: Boolean)
  extends Actor with ActorLogging with Timers {

  import ExecutionTracing._
  import Proposer._

  private implicit val implId: NodeId = nodeId
  private implicit val implLog: LoggingAdapter = log

  private val minQuorumSize = nodeCount/2 + 1

  // @todo injected from outside
  private var communicator: ActorRef = _
  // @todo del
  private var mostRecentlySeenInstanceId: InstanceId = 0
  private val idGen = new IdGenerator(nodeId)

  private var paxosState: Option[PaxosInstanceState] = None
  private def state[T] = paxosState.get.asInstanceOf[T]

  // @todo no longer needed we get only one request, after we finish instance we are done
  private val rqQueue = new util.LinkedList[KeyValue]

  // @todo not needed
  override def preStart(): Unit = {
    super.preStart()

    learner ! LearnerSubscribe
  }

  // @todo simply alias to phase1
  override def receive = {
    case Ready =>
      logg(ContextChange("ready"))

      communicator = sender()
      logg(CommInitialized(communicator))

      // @todo attach logger in local test scenario
      if (rqQueue.size() > 0) {
        /* process messages that were enqueued while we were waiting for UDP */
        logg(ContextChange("phase1"))
        context.become(phase1)
        self ! Start
      } else {
        /* otherwise just go to idle */
        logg(ContextChange("idle"))
        context.become(idle)
      }


    case KvsSend(key, value) =>
      val kv = KeyValue(key, value)
      rqQueue.addLast(kv)
      logg(RequestQueued(kv))
  }

  // @todo del
  def idle: Receive = {
    case KvsSend(key, value) =>
      rqQueue.add(KeyValue(key, value))
      context.become(phase1)
      self ! Start

      logg(RequestQueued(KeyValue(key, value), "idle"))
      logg(ContextChange("phase1"))
  }

  def phase1: Receive = {
    // @todo del
    case KvsSend(key, value) =>
      rqQueue.addLast(KeyValue(key, value))
      logg(RequestQueued(KeyValue(key, value), "phase1"))

    // @todo del
    case Start if rqQueue.size() == 0 =>
      logg(QueueEmpty())
      logg(ContextChange("idle"))
      context.become(idle)

    // @todo start without any conditions + self ! Start from constructor
    case Start if rqQueue.size() > 0 =>
      val v = rqQueue.pop() // d
      // @todo requestprocessing doesn't hold value, but instance id (no value at this point)
      logg(RequestProcessingStarted(v))

      // @todo not our responsibility really
      val iid = mostRecentlySeenInstanceId + 1
      val rid = idGen.nextId()
      val mo = RoundIdentifier(iid, rid)

      communicator ! SendMulticast(Prepare(mo))
      // @todo preparesent without value
      logg(PrepareSent(mo, v))
      startTimer(p1Conf)

      mostRecentlySeenInstanceId += 1
      // @todo no vlaue in phase 1 message
      paxosState = Some(Phase1(mo, v))

    // @todo there shouldn't be a situation when state is undefined
    case ReceivedMessage(m @ ConsensusMessage(messageMo), sid) if paxosState.isDefined =>
      val Some(PaxosInstanceState(currentMo)) = paxosState

      // @todo check for roudn only, not instance
      if(messageMo == currentMo) {
        val st = state[Phase1]

        m match {
          case RoundTooOld(_, mostRecent) =>
            // someone is already using this instance - we need to switch to new one
            // we didn't sent 2a yet, so we can simply abandon this instance and try for a new one

            // just for comletness (in case mechanism changes later)
            st.rejectors += sid

            // @todo report to parent and terminate
            paxosState = None
            stopTimer(p1Conf)
            rqQueue.addFirst(st.ourValue)
            logg(RoundOverridden(currentMo, mostRecent, "1b"))
            self ! Start

          case pm @ Promise(_, vr, ovv) =>
            if (st.promises.contains(sid)) {
              logg(PromiseDuplicate(sid, pm))
            } else {
              st.promises += (sid -> RPromise(vr, ovv))
              logg(NewPromise(sid, pm))

              if (st.promises.size >= minQuorumSize) {
                stopTimer(p1Conf)

                val v = pickValueToVote(st.promises, currentMo.roundId).getOrElse(st.ourValue)

                communicator ! SendMulticast(AcceptRequest(currentMo, v))
                logg(InitiatingVoting(currentMo, v, st.ourValue))

                // enter Phase 2
                paxosState = Some(Phase2(currentMo, v, st.ourValue))
                context.become(phase2)
                logg(ContextChange("phase2"))
                startTimer(p2Conf)
                startTimer(tConf)
              }
            }


          case _ =>
        }

      } else {
        // @todo most external logging not needed
        if (messageMo.instanceId != currentMo.instanceId) {
          logg(IgnoringInstance(messageMo.instanceId, currentMo.instanceId, m))
        } else {
          if (messageMo.roundId > currentMo.roundId) {
            logg(IgnoringRound(messageMo.instanceId, messageMo.roundId, currentMo.roundId, m))
          } else if (messageMo.roundId < currentMo.roundId) {
            logg(IgnoringRound(messageMo.instanceId, messageMo.roundId, currentMo.roundId, m))
          }
        }
    }

    case P1Tick =>
      val st = state[Phase1]
      val alive = st.promises.keySet ++ st.rejectors

      (0 until nodeCount).filterNot(id => alive.contains(id) || id == nodeId).foreach(id => {
        val msg = Prepare(st.mo)
        communicator ! SendUnicast(msg, id)
      })

      logg(TimeoutHit(TimeoutType.p1b, s"retransmitting to ${nodeCount - alive.size} nodes"))

  }

  def phase2: Receive = {
    // we wait for results of round we initiated and we have to make a decision - do we restart or value was voted for
    case KvsSend(key, value) =>
      rqQueue.addLast(KeyValue(key, value))
      logg(RequestQueued(KeyValue(key, value), "phase2"))

    case ValueLearned(iid, k, v) =>
      val votedValue = KeyValue(k,v)
      val st = state[Phase2]
      if (st.mo.instanceId == iid) {
        // OK, results of our round are published
        if (votedValue == st.ourValue) {
          logg(InstanceSuccessful(iid))
          // mission accomplished, we can go back to idle
          paxosState = None
          context.become(phase1)
          logg(ContextChange("phase1"))
          self ! Start
        } else {
          logg(VotingUnsuccessful(iid, votedValue))
          /*
          we have to try once again
          but we might have a problem - if we were trying to complete prevously initiated round, what
          value should we try to propose in a new round? the one we were trying to push or the one we originally
          wanted?

          currently, the old one is simply _dropped_
           */

          // @todo remove all queues, simply inform actor
          paxosState = None
          rqQueue.addFirst(st.ourValue)

          // @todo this looks fishy, probably'll result in some pattern matching errors in ReceiveMessage
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
      // @todo inform parent
      state[Phase2].nacks += sid
      logg(RoundOverridden(mmo, higherId, "2b"))
      ()

    case P2Tick =>
      val cst = state[Phase2]
      val msg = AcceptRequest(cst.mo, cst.votedValue)
      (0 until nodeCount).filterNot(id => cst.nacks.contains(id) || id == nodeId).foreach(id => {
        communicator ! SendUnicast(msg, id)
      })
      logg(TimeoutHit(TimeoutType.p2b, "retransmitting 2a message"))

    case Timeout =>
      /* timeout, we give up */
      logg(TimeoutHit(TimeoutType.instance, "abandoning"))

      paxosState = None
      context.become(phase1)
      logg(ContextChange("phase1"))
      self ! Start
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
        FileLog.error(s"Got some illegal RPromise: $other")
    }

    if (largestFound > currentRid) {
      FileLog.error(s"Stumbled upon something illegal while sifting through received proposals /2A, " +
        s"rid ($largestFound) > currentRid ($currentRid)/")
    }

    if (largest.size > 1) {
      FileLog.error(s"Quorum reported in 1B more than one value: $largest")
    }

    largest.headOption
  }

  // @todo move timers to utils
  def startTimer(conf: TimerConf) =
    if (!disableTimeouts)
      timers.startPeriodicTimer(conf.key, conf.msg, FiniteDuration(conf.msInterval, TimeUnit.MILLISECONDS))

  def stopTimer(conf: TimerConf) =
    if (!disableTimeouts)
      timers.cancel(conf.key)

  def logg(msg: LogMessage): Unit = loggers.foreach(_ ! msg)

}
