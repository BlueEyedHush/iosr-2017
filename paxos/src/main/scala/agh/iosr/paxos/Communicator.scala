package agh.iosr.paxos

import java.net.InetSocketAddress

import agh.iosr.paxos.Messages.{ReceivedMessage, SendMulticast, SendUnicast}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Udp}

object Communicator {
  def props(master: ActorRef, clusterAddresses: Map[String, List[InetSocketAddress]], myAddress: String, myPort: Int = 0): Props =
    Props(classOf[Communicator], master, clusterAddresses, myAddress, myPort)
}

class Communicator(master: ActorRef, clusterAddresses: Map[String, List[InetSocketAddress]], myAddress: String, myPort: Int = 0)
  extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(myAddress, myPort))

  def receive = {
    case Udp.Bound(_) =>
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) => master ! ReceivedMessage(SerializationHelper.deserialize(data), remote)
    case SendUnicast(data, remote) => socket ! Udp.Send(SerializationHelper.serialize(data), remote)
    case SendMulticast(data, destination) =>
      val serializedData = SerializationHelper.serialize(data)
      clusterAddresses(destination).foreach {
        remote => socket ! Udp.Send(serializedData, remote)
      }
  }
}