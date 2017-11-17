package agh.iosr.paxos.actors

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
  def props(dispatcher: ActorRef,
            communicator: ActorRef,
            ourInstanceId: InstanceId,
            nodeId: NodeId,
            nodeCount: NodeId,
            loggers: Set[ActorRef] = Set(),
            disableTimeouts: Boolean = false): Props =
    Props(new Proposer(dispatcher, communicator, ourInstanceId, nodeId, nodeCount, loggers, disableTimeouts))

  case class RPromise(lastRoundVoted: RoundId, ov: Option[KeyValue])

  type PromiseMap = mutable.Map[NodeId, RPromise]

  case object Start

  case object P1Tick
  case object P2Tick
  case object Timeout

  private val p1Conf = TimerConf("p1a", 200, P1Tick)
  private val p2Conf = TimerConf("p2a", 200, P2Tick)
  private val tConf = TimerConf("timeout", 2000, Timeout)

  sealed trait Result {
    def iid: InstanceId
  }

  case class OurValueChosen(iid: InstanceId, v: KeyValue) extends Result
  case class OverrodeInP1(iid: InstanceId) extends Result
  case class OverrodeInP2(iid: InstanceId) extends Result
  case class InstanceTimeout(iid: InstanceId) extends Result
}

// @todo move messages here
// @todo add type to SendableMessage, modify all case expressions
// @todo on timeout actor should terminate
// @todo right after initialization it should initiate phase 1
// @todo rewrite FileLog (but also need to modify parser)
// @todo maybe eliminate case-class based states altogether?
object ExecutionTracing {
  object TimeoutType extends Enumeration {
    val p1b, p2b, instance = Value
  }

  case class RequestProcessingStarted(rid: RoundIdentifier) extends LogMessage
  case class QueueEmpty() extends LogMessage

  case class ContextChange(to: String) extends LogMessage
  case class IgnoringRound(instance: InstanceId, ignored: RoundId, current: RoundId, m: SendableMessage) extends LogMessage

  /* phase 1 */
  case class PrepareSent(ri: RoundIdentifier) extends LogMessage

  case class NewPromise(sender: NodeId, cm: Promise) extends LogMessage
  case class PromiseDuplicate(sender: NodeId, cm: Promise) extends LogMessage
  case class RequestQueued(req: KeyValue, phase: String = "") extends LogMessage

  /* phase 2 */
  case class InitiatingVoting(ri: RoundIdentifier, proposal: KeyValue, ourV: Option[KeyValue]) extends LogMessage
  case class InstanceSuccessful(instance: InstanceId) extends LogMessage
  case class VotingUnsuccessful(instance: InstanceId, value: KeyValue) extends LogMessage
  case class TimeoutHit(which: TimeoutType.Value, comment: String = "") extends LogMessage

  /* p1 and p2 nack */
  case class RoundOverridden(ri: RoundIdentifier, higherId: RoundId, phase: String) extends LogMessage
}


class Proposer(val dispatcher: ActorRef,
               val communicator: ActorRef,
               val ourInstanceId: InstanceId,
               val nodeId: NodeId,
               val nodeCount: NodeId,
               val loggers: Set[ActorRef],
               val disableTimeouts: Boolean)
  extends Actor with ActorLogging with Timers {

  import ExecutionTracing._
  import Proposer._

  private implicit val implId: NodeId = nodeId
  private implicit val implLog: LoggingAdapter = log

  private val minQuorumSize = nodeCount/2 + 1

  private val idGen = new IdGenerator(nodeId)

  private var currentRoundId: RoundIdentifier = _
  private var ourValue: Option[KeyValue] = None
  private var votedValue: Option[KeyValue] = None
  private var rejectors: mutable.Set[NodeId] = mutable.Set()
  private var promises: PromiseMap = mutable.Map[NodeId, RPromise]()
  private var nacks: mutable.Set[NodeId] = mutable.Set()

  private def initiateVoting(v: KeyValue) = {
    assert(votedValue.isEmpty)
    votedValue = Some(v)

    communicator ! SendMulticast(AcceptRequest(currentRoundId, v))
    logg(InitiatingVoting(currentRoundId, v, ourValue))

    context.become(phase2)
    logg(ContextChange("phase2"))
    startTimer(p2Conf)
    startTimer(tConf)
  }

  override def receive = phase1

  def phase1: Receive = {
    case KvsSend(key, value) =>
      assert(ourValue.isEmpty)
      ourValue = Some(KeyValue(key, value))
      logg(RequestQueued(ourValue.get, "phase1"))

    case Start =>
      val rid = idGen.nextId()
      currentRoundId = RoundIdentifier(ourInstanceId, rid)

      logg(RequestProcessingStarted(currentRoundId))

      communicator ! SendMulticast(Prepare(currentRoundId))
      logg(PrepareSent(currentRoundId))
      startTimer(p1Conf)

    case ReceivedMessage(m @ ConsensusMessage(messageMo), sid) =>

      if (messageMo.roundId != currentRoundId.roundId) {
        logg(IgnoringRound(messageMo.instanceId, messageMo.roundId, currentRoundId.roundId, m))
      } else {
        m match {
          case RoundTooOld(_, mostRecent) =>
            // someone is already using this instance - we need to switch to new one
            // we didn't sent 2a yet, so we can simply abandon this instance and try for a new one

            // just for comletness (in case mechanism changes later)
            rejectors += sid
            stopTimer(p1Conf)
            logg(RoundOverridden(currentRoundId, mostRecent, "1b"))
            
            dispatcher ! OverrodeInP1(currentRoundId.instanceId)
            // self ! PoisonPill

          case pm @ Promise(_, vr, ovv) =>
            if (promises.contains(sid)) {
              logg(PromiseDuplicate(sid, pm))
            } else {
              promises += (sid -> RPromise(vr, ovv))
              logg(NewPromise(sid, pm))

              if (promises.size >= minQuorumSize) {
                stopTimer(p1Conf)

                pickValueToVote(promises, currentRoundId.roundId) match {
                  case Some(previousV) =>
                    /* we have to handle previous value, we must inform dispatcher */
                    dispatcher ! OverrodeInP1(currentRoundId.instanceId)
                    initiateVoting(previousV)
                  case None =>
                    /* we are free to select any value we want */
                    ourValue match {
                      case Some(ourV) =>
                        /* start voting and go to phase 2 */
                        initiateVoting(ourV)
                      case None =>
                        /* go to waiting state */
                        context.become(waitingForValue)
                        logg(ContextChange("waitingForValue"))
                    }
                }
              }
            }
          }
      }


    case P1Tick =>
      val alive = promises.keySet ++ rejectors

      (0 until nodeCount).filterNot(id => alive.contains(id) || id == nodeId).foreach(id => {
        val msg = Prepare(currentRoundId)
        communicator ! SendUnicast(msg, id)
      })

      logg(TimeoutHit(TimeoutType.p1b, s"retransmitting to ${nodeCount - alive.size} nodes"))

  }

  def waitingForValue: Receive = {
    case KvsSend(key, value) =>
      assert(ourValue.isEmpty)
      ourValue = Some(KeyValue(key, value))
      logg(RequestQueued(ourValue.get, "waitingForValue"))

      initiateVoting(ourValue.get)
  }

  def phase2: Receive = {
    // we wait for results of round we initiated and we have to make a decision - do we restart or value was voted for
    case ValueLearned(iid, k, v) =>
      assert(ourValue.isDefined)
      val ourV = ourValue.get

      val votedValue = KeyValue(k,v)
        // OK, results of our round are published
      if (votedValue == ourV) {
        logg(InstanceSuccessful(iid))
        dispatcher ! OurValueChosen(currentRoundId.instanceId, ourV)
        // self ! PoisonPill
      } else {
        logg(VotingUnsuccessful(iid, votedValue))
        /*
        we have to try once again
        but we might have a problem - if we were trying to complete prevously initiated round, what
        value should we try to propose in a new round? the one we were trying to push or the one we originally
        wanted?

        currently, the old one is simply _dropped_
         */

        dispatcher ! OverrodeInP2(currentRoundId.instanceId)
        // self ! PoisonPill
      }

      stopTimer(p2Conf)
      stopTimer(tConf)

    case ReceivedMessage(HigherProposalReceived(mmo, higherId), sid) =>
      // @todo handle it better, wait initially for more nodes?
      nacks += sid
      logg(RoundOverridden(mmo, higherId, "2b"))
      dispatcher ! OverrodeInP2(currentRoundId.instanceId)
      // self ! PoisonPill

      // @todo actuall set votedValue!
    case P2Tick =>
      assert(votedValue.isDefined)
      val msg = AcceptRequest(currentRoundId, votedValue.get)
      (0 until nodeCount).filterNot(id => nacks.contains(id) || id == nodeId).foreach(id => {
        communicator ! SendUnicast(msg, id)
      })
      logg(TimeoutHit(TimeoutType.p2b, "retransmitting 2a message"))

    case Timeout =>
      /* timeout, we give up */
      logg(TimeoutHit(TimeoutType.instance, "abandoning"))

      dispatcher ! InstanceTimeout(currentRoundId.instanceId)
      // self ! PoisonPill
  }

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
