package agh.iosr.paxos.actors

import java.net.InetSocketAddress

import agh.iosr.paxos._
import agh.iosr.paxos.messages.SendableMessage
import agh.iosr.paxos.predef.{IdToIpMap, IpToIdMap, NodeId}
import agh.iosr.paxos.utils.Serializer
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Udp}

case class SendUnicast(data: SendableMessage, remote: NodeId)
case class SendMulticast(data: SendableMessage)
case class ReceivedMessage(data: SendableMessage, remote: NodeId)
case object Ready

object Communicator {
  def props(subscribers: Set[ActorRef], me: InetSocketAddress, ipToId: IpToIdMap, idToIpMap: IdToIpMap): Props =
    Props(new Communicator(subscribers, me, ipToId, idToIpMap))
}

class Communicator(subscribers: Set[ActorRef], myAddress: InetSocketAddress, ipToIdMap: IpToIdMap, idToIpMap: IdToIpMap)
  extends Actor with ActorLogging {

  import context.system
  private val serializer = new Serializer()

  IO(Udp) ! Udp.Bind(self, myAddress)

  def receive: Receive = {
    case Udp.Bound(_) =>
      subscribers.foreach(_ ! Ready)
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remoteIp) =>
      val remoteId: NodeId = if (remoteIp != null) ipToIdMap(remoteIp) else predef.NULL_NODE_ID
      subscribers.foreach(_ ! ReceivedMessage(serializer.deserialize(data), remoteId))

    case SendUnicast(data, remoteId) =>
      socket ! Udp.Send(serializer.serialize(data), idToIpMap(remoteId))

    case SendMulticast(data) =>
      val serializedData = serializer.serialize(data)
      ipToIdMap.keys.foreach(remoteIp => socket ! Udp.Send(serializedData, remoteIp))
  }
}
