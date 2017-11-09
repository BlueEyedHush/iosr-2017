package agh.iosr.paxos

import agh.iosr.paxos.Messages._
import agh.iosr.paxos.predef._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import scala.collection.mutable

case class InstanceState(lastParticipated: RoundId, lastVoted: RoundId, vote: KeyValue, remote: NodeId)

object Acceptor {
  def props(): Props =
    Props(new Acceptor())
}

class Acceptor()
  extends Actor with ActorLogging {

  var communicator: ActorRef = _
  var runningInstances: mutable.Map[InstanceId, InstanceState] = mutable.Map.empty
  var highestInstance: InstanceId = NULL_ROUND

  override def receive: Receive = {
    case Ready =>
      communicator = sender()
      context.become(ready)
  }

  def ready: Receive = {
    case ReceivedMessage(data, remoteId) =>

      data match {
        case Prepare(MessageOwner(instanceId, roundId)) =>

          runningInstances.getOrElse(instanceId, InstanceState(NULL_ROUND, NULL_ROUND, NULL_KEY_VALUE, NULL_NODE_ID)) match {
            case InstanceState(NULL_ROUND, _, _, _) =>
              runningInstances(instanceId) = InstanceState(roundId, NULL_ROUND, NULL_KEY_VALUE, remoteId)
              highestInstance = math.max(highestInstance, instanceId)
              communicator ! SendUnicast(Promise(MessageOwner(instanceId, roundId), NULL_ROUND, NULL_KEY_VALUE), remoteId)

            case InstanceState(lastParticipated, _, _, lastRemote)
              if roundId <= lastParticipated && remoteId != lastRemote =>
                communicator ! SendUnicast(RoundTooOld(MessageOwner(instanceId, roundId), highestInstance), remoteId)

            case InstanceState(lastParticipated, lastVoted, vote, lastRemote)
              if roundId > lastParticipated || (roundId == lastParticipated && remoteId == lastRemote) =>
                runningInstances(instanceId) = InstanceState(roundId, lastVoted, vote, remoteId)
                communicator ! SendUnicast(Promise(MessageOwner(instanceId, roundId), lastVoted, vote), remoteId)
          }

        case AcceptRequest(MessageOwner(instanceId, roundId), value) =>

          runningInstances.getOrElse(instanceId, InstanceState(NULL_ROUND, NULL_ROUND, NULL_KEY_VALUE, NULL_NODE_ID)) match {
            case InstanceState(lastParticipated, lastVoted, vote, lastRemote)
              if roundId >= lastParticipated && (roundId != lastVoted || (value == vote && remoteId == lastRemote))=>
                runningInstances(instanceId) = InstanceState(roundId, roundId, value, remoteId)
                highestInstance = math.max(highestInstance, instanceId)
                communicator ! SendMulticast(Accepted(MessageOwner(instanceId, roundId), value))

            case InstanceState(lastParticipated, _, _, _) =>
              communicator ! SendUnicast(HigherProposalReceived(MessageOwner(instanceId, roundId), lastParticipated), remoteId)
          }
      }
  }

}