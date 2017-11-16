package agh.iosr.paxos.actors

import agh.iosr.paxos.messages.Messages.KvsSend
import agh.iosr.paxos.predef._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

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

  private var currentBatchOffset: InstanceId = NULL_INSTANCE_ID
  private var offsetWithinBatch: InstanceId = NULL_INSTANCE_ID
  private var proposers: Array[ActorRef] = _

  override def receive = follower

  // @todo MessageReceived message passthrough (comining functions?) - not done in elector
  // @todo must be ready to handle signals send from paxos instances (including retry)
  // @todo sent start to actor
  // @todo proposers who never received value are going to hang...
  // @todo keep tracck of what value send to whom

  def follower: Receive = {
    case BecomingLeader =>
      allocateInstances()
      context.become(leader)

    case m @ KvsSend(_, _) =>
      log.error(s"Dispatcher received $m while being in notLeader state")
  }

  def leader: Receive = {
    case LoosingLeader =>
      context.become(follower)

    case m @ KvsSend(_, _) =>
      // @todo request new batch if needed, find free instance and send message to it
      if (offsetWithinBatch == batchSize)
        allocateInstances()

      proposers(offsetWithinBatch) ! m

      offsetWithinBatch += 1
  }

  private def allocateInstances() = {

  }
}
