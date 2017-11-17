package agh.iosr.paxos.actors

import agh.iosr.paxos.actors.Dispatcher.ProposerProvider
import agh.iosr.paxos.actors.Proposer._
import agh.iosr.paxos.messages.Messages.{ConsensusMessage, KvsSend, ValueLearned}
import agh.iosr.paxos.predef._
import agh.iosr.paxos.utils.LogMessage
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, PoisonPill, Props}

import scala.collection._

object DispatcherExecutionTracing {
  case class ContextChange(to: String) extends LogMessage
  case class PaxosMessageForwarding(m: Any, success: Boolean, currentBatchOffset: InstanceId) extends LogMessage
  case class DispatchedValueToPaxosInstance(v: KeyValue, cause: Any) extends LogMessage
  case class IllegalMessage(m: Any, illegalityCause: String) extends LogMessage
  case class BatchAllocated() extends LogMessage
  case class UnusedDistanceDisposedOf() extends LogMessage
}

object Dispatcher {
  val batchSize = 10

  // dispatcher, comm, currentBatchOffset + id, nodeId, nodeCount
  type ProposerProvider = (ActorContext, ActorRef, ActorRef, InstanceId, NodeId, NodeId) => ActorRef

  val defaultProvider: ProposerProvider = (context, dispatcher, comm, iid, nodeId, nodeCount) =>
    context.system.actorOf(Proposer.props(dispatcher, comm, iid, nodeId, nodeCount))

  def props(comm: ActorRef,
            learner: ActorRef,
            nodeId: NodeId,
            nodeCount: NodeId,
            loggers: Set[ActorRef] = Set(),
            provider: ProposerProvider = defaultProvider): Props =
    Props(new Dispatcher(comm, learner, nodeId, nodeCount, loggers))
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
  
class Dispatcher(val comm: ActorRef,
                 val learner: ActorRef,
                 val nodeId: NodeId,
                 val nodeCount: NodeId,
                 loggers: Set[ActorRef],
                 provider: ProposerProvider)
  extends Actor with ActorLogging {

  import Dispatcher._
  import DispatcherExecutionTracing._
  import Elector._

  private var currentBatchOffset: InstanceId = NULL_INSTANCE_ID
  private var nextFreeInPool: InstanceId = NULL_INSTANCE_ID
  private val instanceMap = mutable.Map[InstanceId, (KeyValue, ActorRef)]()
  private var freePool: Array[ActorRef] = _

  override def receive = follower

  def tryForward(iid: InstanceId, m: Any) = {
    /* forward message to correct instance (if such an instance has been registered) */
    val instanceOption =  instanceMap.get(iid)
    if (instanceOption.isDefined) {
      val (_, instance) = instanceOption.get
      instance ! m
      logg(PaxosMessageForwarding(m, success = true, currentBatchOffset))
    } else {
      val nfiid = currentBatchOffset + nextFreeInPool
      logg(PaxosMessageForwarding(m, success = false, currentBatchOffset))
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

        val res = m.asInstanceOf[Result]
        logg(DispatchedValueToPaxosInstance(instanceMap(m.iid)._1, m))
      }

      instanceMap.remove(m.iid)
  }

  val followerHandler: Receive = {
    case BecomingLeader =>
      allocateInstances()
      context.become(leader)
      logg(ContextChange("leader"))

    case m @ KvsSend(_, _) =>
      logg(IllegalMessage(m, "received KvsSend while being follower"))
  }

  val leaderHandler: Receive = {
    case LoosingLeader =>
      context.become(follower)
      logg(ContextChange("follower"))

    case m @ KvsSend(k,v) =>
      /* request new batch if needed, find free instance and send message to it */
      if (nextFreeInPool == batchSize)
        allocateInstances()

      val value = KeyValue(k,v)
      val proposer = freePool(nextFreeInPool)
      val iid = currentBatchOffset + nextFreeInPool
      nextFreeInPool += 1

      instanceMap += (iid -> (value, proposer))
      proposer ! m

      logg(DispatchedValueToPaxosInstance(value, m))
  }

  def follower: Receive = followerHandler orElse
    proposerResultHandler(recreate = false) orElse
    messageReceivedPassthrough

  def leader: Receive = leaderHandler orElse
    proposerResultHandler(recreate = true) orElse
    messageReceivedPassthrough



  private def allocateInstances() = {
    /* first clean up instances left over from previous sessions */
    (nextFreeInPool until batchSize).foreach(id => {
      val instance = freePool(id)
      instance ! PoisonPill
      logg(UnusedDistanceDisposedOf())
    })

    /* update counters for new batch */
    nextFreeInPool = 0
    currentBatchOffset += batchSize

    /* allocate new instances */
    freePool = (0 until batchSize).map(id => {
      val r = provider(context, self, comm, currentBatchOffset + id, nodeId, nodeCount)
      r ! Start
      r
    }).toArray

    logg(BatchAllocated())
  }

  def logg(msg: LogMessage): Unit = loggers.foreach(_ ! msg)
}
