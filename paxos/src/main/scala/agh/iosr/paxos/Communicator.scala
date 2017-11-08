package agh.iosr.paxos

import java.net.InetSocketAddress

import agh.iosr.paxos.predef.{IdToIpMap, IpAddress, IpToIdMap}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Udp}

case class SendUnicast(data: SendableMessage, remote: InetSocketAddress)
case class SendMulticast(data: SendableMessage, destination: String)
case class ReceivedMessage(data: SendableMessage, remote: InetSocketAddress)

object Communicator {
  def props(master: ActorRef, me: IpAddress, ipToId: IpToIdMap, idToIpMap: IdToIpMap): Props =
    Props(new Communicator(master, me, ipToId, idToIpMap))
}

class Communicator(master: ActorRef, me: IpAddress, ipToId: IpToIdMap, idToIpMap: IdToIpMap)
  extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.Bind(self, me.toInetAddress)

  def receive = {
    case Udp.Bound(_) =>
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) => master ! ReceivedMessage(SerializationHelper.deserialize(data), remote)
    case SendUnicast(data, remote) => socket ! Udp.Send(SerializationHelper.serialize(data), remote)
    case SendMulticast(data, _) =>
      val serializedData = SerializationHelper.serialize(data)
      ipToId.keys.foreach(ip => socket ! Udp.Send(serializedData, ip.toInetAddress))
  }
}