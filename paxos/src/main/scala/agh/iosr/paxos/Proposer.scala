package agh.iosr.paxos

import agh.iosr.paxos.Messages._
import agh.iosr.paxos.predef._
import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.collection._

/*
Checklist:
* duplicate messages
* retransmission
 */

object Metadata {

  sealed trait InstanceState

  case class RPromise(lastRoundVoted: RoundId, ov: Option[PaxosValue])

  case class Idle() extends InstanceState

  case class ExecutingInstance(var iid: InstanceId, var mostRecentRound: RoundId) extends InstanceState

  type PromiseMap = mutable.Map[NodeId, RPromise]

  // @todo: maybe they should be oridnary classes?
  case class P1aSent(_iid: InstanceId,
                     _mrr: RoundId,
                     v: PaxosValue,
                     promises: PromiseMap = mutable.Map[NodeId, RPromise]())
    extends InstanceState(_iid, _mrr)

  case class P2aSent(_iid: InstanceId, _mrr: RoundId, votedValue: PaxosValue, ourValue: PaxosValue)
    extends InstanceState(_iid, _mrr)

  // retransmission timer

  case class Start(v: PaxosValue)
}

class Proposer(val nodeId: NodeId, val nodeCount: NodeId) extends Actor with ActorLogging {
  // @todo subscribe with learner
  import Metadata._

  private val minQuorumSize = nodeCount/2 + 1

  private var communicator: ActorRef = _
  private var mostRecentlySeenInstanceId: InstanceId = 0
  private val idGen = new IdGenerator(nodeId)

  private var is: InstanceState = _

  private def state[T] = is.asInstanceOf[T]


  override def receive = {
    case Ready =>
      communicator = sender()
      context.become(idle)
  }

  def idle: Receive = {
    case KvsSend(key, value) =>
      context.become(executing)
      self ! Start(PaxosValue(key, value))
  }

  def executing: Receive = {
    // @todo: retransmission

    case KvsSend(key, value) => ???

    case Start(v) =>
      val iid = mostRecentlySeenInstanceId + 1
      val rid = idGen.nextId()
      val mo = MessageOwner(iid, rid)
      // @todo: FSM - should be sent upon entering executing state
      communicator ! SendMulticast(Prepare(mo))

      mostRecentlySeenInstanceId += 1
      is = P1aSent(iid, rid, v)

    // @todo: write unapply for ConsensusMessae and pattern match here
    case ReceivedMessage(m, sid) => {
      case m: ConsensusMessage =>
        val st = state[ExecutingInstance]
        val MessageOwner(iid, rid) = m.mo
        if (iid == st.iid) {
          if (rid == st.mostRecentRound) {
            st match {
              case P1aSent(_, _, ourV, promises) => m match {
                case RoundTooOld(_, mostRecent) =>
                  // @todo when we gain possibility of ID correction, modify it here
                  // someone is already using this instance - we need to switch to new one
                  // we didn't sent 2a yet, so we can simply abandon this instance and try for a new one
                  is = Idle()
                  self ! Start(ourV)
                case Promise(_, vr, ovv) =>
                  val st1a = state[P1aSent]
                  if (st1a.promises.contains(sid)) {
                    log.info(s"Already got promise from $sid, must be a duplicate: $m")
                  } else {
                    st1a.promises += (sid -> RPromise(vr, ovv))

                    if(st1a.promises.size >= minQuorumSize) {
                      val v = pickValueToVote(st1a.promises, st.mostRecentRound).getOrElse(ourV)

                      val currentMo = MessageOwner(st.iid, st.mostRecentRound)
                      communicator ! SendMulticast(AcceptRequest(currentMo, v))

                      // enter Phase 2
                      is = P2aSent(st.iid, st.mostRecentRound, v, ourV)
                      context.become(waitingForResults)
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

  }

  def waitingForResults: Receive = {
    // we wait for results of round we initiated and we have to make a decision - do we restart or value was voted for
    case KvsSend(key, value) => ???

    // @todo timeout
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
          context.become(executing)
          self ! Start(st.ourValue)
        }
      }

    case HigherProposalReceived(_, higherId) =>
      // higher proposal appeared while our has been voted on -> but we cannot back off now
      // but what if our proposal has actually been accepted? we probably need to wait for the result, otherwise
      // we might overwrite some values
      // all in all, we cannot do much here
      ()

  }

  // @todo make short names longer
  // @todo handling of errant messages in all states

  // @todo: extract and test separatelly
  private def pickValueToVote(pm: PromiseMap, currentRid: RoundId): Option[PaxosValue] = {
    val largest = mutable.Set[PaxosValue]()

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

}
