package agh.iosr.paxos.utils

import java.net.InetSocketAddress

import agh.iosr.paxos.actors._
import agh.iosr.paxos.predef.{IpToIdMap, NodeId}
import akka.actor.{ActorRef, ActorSystem, Props}

import scala.collection.mutable

case class NodeEntry(system: ActorSystem, proposer: ActorRef, acceptor: ActorRef, learner: ActorRef, kvStore: ActorRef, communicator: ActorRef)
case object ElementNotFound extends Exception

class LocalClusterSetupManager(val clusterMap: IpToIdMap, val listenerIp: InetSocketAddress) {
  private val combinedIpToIdMap = clusterMap + (listenerIp -> clusterMap.size)
  private val combinedIdToIpMap = combinedIpToIdMap.map(_.swap)

  var nodes: mutable.Map[NodeId, NodeEntry] = mutable.Map.empty

  def setup(): Unit = {
    clusterMap.foreach {
      case (address, id) =>
        val system = ActorSystem("Node" + id)
        val acceptor = system.actorOf(Props(new Acceptor()))
        val learner = system.actorOf(Props(new Learner()))
        val communicator = system.actorOf(Communicator.props(Set(acceptor, learner), address, combinedIpToIdMap, combinedIdToIpMap))
        nodes += (id -> NodeEntry(system, null, acceptor, learner, null, communicator))
      case _ =>
    }
  }

  def terminate(): Unit = {
    clusterMap.foreach {
      case (_, id) => nodes(id).system.terminate()
      case _ =>
    }
  }

  def getCombinedMaps() = (combinedIpToIdMap, combinedIdToIpMap)

  def getActorSystem(nodeId: NodeId): Option[ActorSystem] = {
    nodes.get(nodeId) match {
      case Some(NodeEntry(system, _, _, _, _, _)) => Option(system)
      case _ => throw ElementNotFound
    }
  }

  def getNodeActor(nodeId: NodeId, actorType: String): Option[ActorRef] = {
    nodes.get(nodeId) match {
      case Some(NodeEntry(_, proposer, acceptor, learner, kvStore, communicator)) =>
        actorType match {
          case "proposer" => Option(proposer)
          case "acceptor" => Option(acceptor)
          case "learner" => Option(learner)
          case "kvStore" => Option(kvStore)
          case "communicator" => Option(communicator)
          case _ => throw ElementNotFound
        }
      case _ => throw ElementNotFound
    }
  }
}
