package agh.iosr.paxos

import agh.iosr.paxos.predef.{IpAddress, NodeId}
import akka.actor.{Actor, Props}
import com.typesafe.config.Config

import scala.collection._

case object GetInfo
case class NodeInfo(myIp: IpAddress,
                    ipToId: immutable.Map[IpAddress, NodeId],
                    idToIp: immutable.Map[NodeId, IpAddress])

object ClusterInfo {
  def props()(implicit config: Config) = Props(new ClusterInfo())
}

class ClusterInfo()(implicit config: Config) extends Actor {
  override def receive = {
    case msg =>
      sender ! msg
  }
}
