package agh.iosr.paxos

import java.net.InetSocketAddress

import agh.iosr.paxos.predef.{IdToIpMap, IpToIdMap, NodeId}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Udp}

case class SendUnicast(data: SendableMessage, remote: NodeId)
case class SendMulticast(data: SendableMessage)
case class ReceivedMessage(data: SendableMessage, remote: NodeId)

object Communicator {
  def props(subscribers: Set[ActorRef], me: InetSocketAddress, ipToId: IpToIdMap, idToIpMap: IdToIpMap): Props =
    Props(new Communicator(subscribers, me, ipToId, idToIpMap))
}

class Communicator(subscribers: Set[ActorRef], me: InetSocketAddress, ipToId: IpToIdMap, idToIpMap: IdToIpMap)
  extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.Bind(self, me)

  def receive = {
    case Udp.Bound(_) =>
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      val id: NodeId = if (remote != null) ipToId(remote) else predef.NULL_NODE_ID
      subscribers.foreach(_ ! ReceivedMessage(SerializationHelper.deserialize(data), id))
    case SendUnicast(data, remote) =>
      val inet = idToIpMap(remote)
      socket ! Udp.Send(SerializationHelper.serialize(data), inet)
    case SendMulticast(data) =>
      val serializedData = SerializationHelper.serialize(data)
      ipToId.keys.foreach(ip => socket ! Udp.Send(serializedData, ip))
  }
}