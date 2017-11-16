package agh.iosr.paxos.actors

import agh.iosr.paxos.actors.Proposer.{InstanceTimeout, OverrodeInP1, OverrodeInP2, Result}
import agh.iosr.paxos.messages.Messages.{ConsensusMessage, KvsSend, ValueLearned}
import agh.iosr.paxos.predef._
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}

import scala.collection._

object Dispatcher {
  val batchSize = 10

  def props(comm: ActorRef, learner: ActorRef): Props = Props(new Dispatcher(comm, learner))
}

/**
  * Manages creation of regular instances
  * Must know when we become leaders in order to reallocate instances
  * After receiving KvsSend from Elector, we immediatelly start new instance
  * Thanks to that we don't need to queue anything
  * After loosing leadership we leave already initiated instances alone - they'll either
  * finish successfully or timeout
  * We don't restart instances that timed out
  *
  */
class Dispatcher(val comm: ActorRef, val learner: ActorRef) extends Actor with ActorLogging {
  import Dispatcher._
  import Elector._

  /* @todo marking as terminated + set of non-terminated instances */

  private var currentBatchOffset: InstanceId = NULL_INSTANCE_ID
  private var nextFreeInPool: InstanceId = NULL_INSTANCE_ID
  private val instanceMap = mutable.Map[InstanceId, (KeyValue, ActorRef)]()
  private val freePool: Array[ActorRef] = _

  override def receive = follower

  // @todo MessageReceived message passthrough (comining functions?) - not done in elector
  // @todo passthrough must also include valuelearned
  // @todo must be ready to handle signals send from paxos instances (including retry)
  // @todo sent start to actor
  // @todo proposers who never received value are going to hang...
  // @todo keep tracck of what value send to whom; unused instances in Set, used go to map, terminate all unsued on becoming leader

  def tryForward(iid: InstanceId, m: Any) = {
    /* forward message to correct instance (if such an instance has been registered) */
    val instanceOption =  instanceMap.get(iid)
    if (instanceOption.isDefined) {
      val (_, instance) = instanceOption.get
      instance ! m
    } else {
      val nfiid = currentBatchOffset + nextFreeInPool
      log.info(s"No match for such RoundIdentifier in our map. [ m = $m, nextFreeInstanceId = $nfiid")
    }
  }

  def restart(iid: InstanceId) = {
    assert(instanceMap.contains(iid))
    val (v, _) = instanceMap(iid)
    self ! KvsSend(v.k, v.v)
  }

  val messageReceivedPassthrough: Receive = {
    case m @ ReceivedMessage(ConsensusMessage(rid), _) => tryForward(rid.instanceId, m)
    case m @ ValueLearned(iid, _, _) => tryForward(iid, m)
  }

  def proposerResultHandler(recreate: Boolean): Receive = {
    case m: Result =>
      sender() ! PoisonPill

      if (recreate) {
        m match {
          case OverrodeInP1(iid) => restart(iid)
          case OverrodeInP2(iid) => restart(iid)
          case InstanceTimeout(iid) => restart(iid)
        }
      }

      instanceMap.remove(m.iid)
  }

  // @todo handle incomin messages from Proposer
  val followerHandler: Receive = {
    case BecomingLeader =>
      allocateInstances()
      context.become(leader)

    case m @ KvsSend(_, _) =>
      log.error(s"Dispatcher received $m while being a follower")
  }

  val leaderHandler: Receive = {
    case LoosingLeader =>
      context.become(follower)

    case m @ KvsSend(k,v) =>
      /* request new batch if needed, find free instance and send message to it */
      if (nextFreeInPool == batchSize)
        allocateInstances()

      val proposer = freePool(nextFreeInPool)
      val iid = currentBatchOffset + nextFreeInPool
      nextFreeInPool += 1

      instanceMap += (iid -> (KeyValue(k,v), proposer))
      proposer ! m
  }

  def follower: Receive = followerHandler orElse
    proposerResultHandler(recreate = false) orElse
    messageReceivedPassthrough

  def leader: Receive = leaderHandler orElse
    proposerResultHandler(recreate = true) orElse
    messageReceivedPassthrough



  private def allocateInstances() = {
    // @todo
  }
}
