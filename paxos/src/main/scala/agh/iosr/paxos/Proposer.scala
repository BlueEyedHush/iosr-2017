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
  case class P1aSent(_iid: InstanceId, _mrr: RoundId, promises: PromiseMap = mutable.Map[NodeId, RPromise]())
    extends InstanceState(_iid, _mrr)

  case class P2aSent(_iid: InstanceId, _mrr: RoundId, promises: PromiseMap = mutable.Map[NodeId, RPromise]())
    extends InstanceState(_iid, _mrr)

  // retransmission timer
}

class Proposer(val nodeId: NodeId, val nodeCount: NodeId) extends Actor with ActorLogging {

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

  // @todo separate methods for idle and executing
  def idle: Receive = {
    case KvsSend(key, value) =>
      val iid = mostRecentlySeenInstanceId + 1
      val rid = idGen.nextId()
      val mo = MessageOwner(iid, rid)
      // @todo: FSM - should be sent upon entering executing state
      communicator ! SendMulticast(Prepare(mo))

      mostRecentlySeenInstanceId += 1

      is = P1aSent(iid, rid)
      context.become(executing)
  }

  def executing: Receive = {
    case KvsSend(key, value) => ???

    // @todo: write unapply for ConsensusMessae and pattern match here
    case ReceivedMessage(m, sid) => {
      case m: ConsensusMessage =>
        val st = state[ExecutingInstance]
        val MessageOwner(iid, rid) = m.mo
        if (iid == st.iid) {
          if (rid == st.mostRecentRound) {
            st match {
              case P1aSent(_, _, promises) => m match {
                case Promise(_, vr, ovv) =>
                  val st1a = state[P1aSent]
                  if (st1a.promises.contains(sid)) {
                    log.info(s"Already got promise from $sid, must be a duplicate: $m")
                  } else {
                    st1a.promises += (sid -> RPromise(vr, ovv))

                    if(st1a.promises.size >= minQuorumSize) {
                      // @todo extract to helper function
                      st1a.
                      // @todo got quorum, can progress to phase 2a
                    }
                  }
              }

                // @todo separate receive for round 2a (but we'd need to repeat all freshness checks...)
              case P2aSent(_, _, promises) => m match {
                case _: Promise =>
                  log.info(s"Got Promise ($m), but already in phase 2, ignoring")
                // @todo handle 2B message
              }

            }
          } else if (rid > st.mostRecentRound) {
            // @todo: backoff and restart with higher instanceId (depending on phase we are in or not?)
          } else if (rid < st.mostRecentRound) {
            log.info(s"Got message from same instance ($iid), but lower round ($rid, current: ${st.mostRecentRound}), " +
              s"ignoring: $m")
          }
        } else {
          log.info(s"Got message from instance ($iid) != current (${st.iid}), ignoring: $m")
        }

    }

  }

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
      log.error(s"Quorum reported in 1B more than one value: $largest");
    }

    largest.headOption
  }

}
