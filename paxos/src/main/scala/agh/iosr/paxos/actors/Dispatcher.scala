package agh.iosr.paxos.actors

import agh.iosr.paxos.messages.Messages.KvsSend
import agh.iosr.paxos.predef._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object Dispatcher {
  val batchSize = 10

  def props(comm: ActorRef): Props = Props(new Dispatcher(comm))
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
class Dispatcher(val comm: ActorRef) extends Actor with ActorLogging {
  import Dispatcher._
  import Elector._

  private var currentBatchOffset: InstanceId = NULL_INSTANCE_ID
  private var offsetWithinBatch: InstanceId = NULL_INSTANCE_ID
  private var proposers: Array[ActorRef] = _

  override def receive = notLeader

  def notLeader: Receive = {
    case BecomingLeader =>
      allocateInstances()
      context.become(leader)

    case m @ KvsSend(_, _) =>
      log.error(s"Dispatcher received $m while being in notLeader state")
  }

  def leader: Receive = {
    case LoosingLeader =>
      context.become(notLeader)

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
