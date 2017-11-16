package agh.iosr.paxos.utils

import agh.iosr.paxos.predef.NodeId
import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingAdapter

trait LogMessage

object Printer {
  def props(nodeId: NodeId): Props = Props(new Printer(nodeId))
}

class Printer(val nodeId: NodeId) extends Actor with ActorLogging {
  private implicit val implId: NodeId = nodeId
  private implicit val implLog: LoggingAdapter = log

  override def receive: Receive = {
    case m => FileLog.info(m)
  }
}
