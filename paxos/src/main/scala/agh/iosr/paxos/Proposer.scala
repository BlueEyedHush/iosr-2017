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

object Metadata {

  sealed trait InstanceState

  case class RPromise(lastRoundVoted: RoundId, ov: Option[KeyValue])

  case class Idle() extends InstanceState

  class ExecutingInstance(var iid: InstanceId, var mostRecentRound: RoundId) extends InstanceState

  type PromiseMap = mutable.Map[NodeId, RPromise]

  // @todo: maybe they should be oridnary classes?
  // @todo put this shit in companion object
  case class P1aSent(_iid: InstanceId,
                     _mrr: RoundId,
                     v: KeyValue,
                     rejectors: mutable.Set[NodeId] = mutable.Set(),
                     promises: PromiseMap = mutable.Map[NodeId, RPromise]())
    extends ExecutingInstance(_iid, _mrr)

  case class P2aSent(_iid: InstanceId,
                     _mrr: RoundId,
                     votedValue: KeyValue,
                     ourValue: KeyValue,
                     nacks: mutable.Set[NodeId] = mutable.Set())
    extends ExecutingInstance(_iid, _mrr)

  // retransmission timer

  case object Start

  case object P1Tick
  case object P2Tick
  case object Timeout
}

object Proposer {
  def props(learner: ActorRef, nodeId: NodeId, nodeCount: NodeId): Props =
    Props(new Proposer(learner, nodeId, nodeCount))
}

class Proposer(val learner: ActorRef, val nodeId: NodeId, val nodeCount: NodeId)
  extends Actor with ActorLogging with Timers {

  import Metadata._

  private val minQuorumSize = nodeCount/2 + 1

  private var communicator: ActorRef = _
  private var mostRecentlySeenInstanceId: InstanceId = 0
  private val idGen = new IdGenerator(nodeId)

  private var is: InstanceState = _
  private def state[T] = is.asInstanceOf[T]

  private val rqQueue = new util.LinkedList[KeyValue]

  override def preStart(): Unit = {
    super.preStart()

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
      context.become(executing)
      self ! Start
  }

  def executing: Receive = {
    case KvsSend(key, value) => rqQueue.addLast(KeyValue(key, value))

    case Start if rqQueue.size() > 0 =>
      val v = rqQueue.pop()

      val iid = mostRecentlySeenInstanceId + 1
      val rid = idGen.nextId()
      val mo = MessageOwner(iid, rid)
      // @todo: FSM - should be sent upon entering executing state
      communicator ! SendMulticast(Prepare(mo))
      startTimer(p1Conf)

      mostRecentlySeenInstanceId += 1
      is = P1aSent(iid, rid, v)

    // @todo: write unapply for ConsensusMessae and pattern match here
    case ReceivedMessage(m, sid) => m match {
      case m: ConsensusMessage =>
        val st = state[ExecutingInstance]
        val MessageOwner(iid, rid) = m.mo
        if (iid == st.iid) {
          if (rid == st.mostRecentRound) {
            st match {
              case P1aSent(_, _, ourV, _, promises) => m match {
                case RoundTooOld(_, mostRecent) =>
                  // @todo when we gain possibility of ID correction, modify it here
                  // someone is already using this instance - we need to switch to new one
                  // we didn't sent 2a yet, so we can simply abandon this instance and try for a new one

                  // just for comletness (in case mechanism changes later)
                  val st1a = state[P1aSent]
                  st1a.rejectors += sid

                  is = Idle()
                  stopTimer(p1Conf)
                  rqQueue.addFirst(ourV)
                  self ! Start
                case Promise(_, vr, ovv) =>
                  val st1a = state[P1aSent]
                  if (st1a.promises.contains(sid)) {
                    log.info(s"Already got promise from $sid, must be a duplicate: $m")
                  } else {
                    st1a.promises += (sid -> RPromise(vr, ovv))

                    if(st1a.promises.size >= minQuorumSize) {
                      stopTimer(p1Conf)

                      val v = pickValueToVote(st1a.promises, st.mostRecentRound).getOrElse(ourV)

                      val currentMo = MessageOwner(st.iid, st.mostRecentRound)
                      communicator ! SendMulticast(AcceptRequest(currentMo, v))

                      // enter Phase 2
                      is = P2aSent(st.iid, st.mostRecentRound, v, ourV)
                      context.become(waitingForResults)
                      startTimer(p2Conf)
                      startTimer(tConf)
                    }
                  }
              }
            }
          } else if (rid > st.mostRecentRound) {
            log.info(s"Higher round notices, but maybe our proposal was chosen before?")
          } else if (rid < st.mostRecentRound) {
            log.info(s"Got message from same instance ($iid), but lower round ($rid, current: ${st.mostRecentRound}), " +
              s"ignoring: $m")
          }
        } else {
          log.info(s"Got message from instance ($iid) != current (${st.iid}), ignoring: $m")
        }
    }

    case P1Tick =>
      val cst = state[P1aSent]
      val alive = cst.promises.keySet ++ cst.rejectors

      (0 until nodeCount).filter(!alive.contains(_)).foreach(id => {
        // @todo helper method for sending (avoid code duplication)
        // @todo make current MO easily accessible
        val msg = Prepare(MessageOwner(cst._iid, cst._mrr))
        communicator ! SendUnicast(msg, id)
      })
  }

  def waitingForResults: Receive = {
    // we wait for results of round we initiated and we have to make a decision - do we restart or value was voted for
    case KvsSend(key, value) => rqQueue.addLast(KeyValue(key, value))

    case ValueLearned(iid, votedValue) =>
      val st = state[P2aSent]
      if (st._iid == iid) {
        // OK, results of our round are published
        if (votedValue == st.ourValue) {
          // mission accomplished, we can go back to idle
          is = Idle()
          context.become(idle)
          // @todo we could also inform user that we are ready for his next request
        } else {
          /*
          we have to try once again
          but we might have a problem - if we were trying to complete prevously initiated round, what
          value should we try to propose in a new round? the one we were trying to push or the one we originally
          wanted?

          currently, the old one is simply _dropped_
           */

          is = Idle()
          rqQueue.addFirst(st.ourValue)
          context.become(executing)
          self ! Start
        }

        stopTimer(p2Conf)
        stopTimer(tConf)
      }

    case ReceivedMessage(HigherProposalReceived(_, higherId), sid) =>
      // higher proposal appeared while our has been voted on -> but we cannot back off now
      // but what if our proposal has actually been accepted? we probably need to wait for the result, otherwise
      // we might overwrite some values
      // all in all, we cannot do much here
      state[P2aSent].nacks += sid
      ()

    case P2Tick =>
      val cst = state[P2aSent]
      val msg = AcceptRequest(MessageOwner(cst._iid, cst._iid), cst.votedValue)
      (0 until nodeCount).filter(cst.nacks.contains).foreach(id => {
        communicator ! SendUnicast(msg, id)
      })

    case Timeout =>
      /* timeout, we give up */
      val st = state[P2aSent]
      log.info(s"Timeout reached, aborting (iid: ${st._iid}, rid: ${st._mrr}")

      is = Idle()
      context.become(idle)
      // @todo: good place to inform client we are free

  }

  // @todo make short names longer
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

  case class TimerConf(key: String, msInterval: Int, msg: Any)
  private val p1Conf = TimerConf("p1a", 200, P1Tick)
  private val p2Conf = TimerConf("p2a", 200, P2Tick)
  private val tConf = TimerConf("timeout", 2000, Timeout)

  def startTimer(conf: TimerConf) = {
    timers.startPeriodicTimer(conf.key, conf.msg, FiniteDuration(conf.msInterval, TimeUnit.MILLISECONDS))
  }

  def stopTimer(conf: TimerConf) = {
    timers.cancel(conf.key)
  }

}
