package agh.iosr.paxos.utils

import agh.iosr.paxos.actors._
import agh.iosr.paxos.predef.{IdToIpMap, NodeId}
import akka.actor.{ActorRef, ActorSystem, Props}

import scala.collection.mutable

case class NodeEntry(system: ActorSystem, proposer: ActorRef, acceptor: ActorRef, learner: ActorRef, kvStore: ActorRef, communicator: ActorRef)

class LocalClusterSetupManager {

  var nodes: mutable.Map[NodeId, NodeEntry] = mutable.Map.empty

  def setup(idToIpMap: IdToIpMap): Unit = {
    val ipToIdMap = idToIpMap.map(_.swap)

    idToIpMap.foreach {
      case (id, address) if id >= 0 =>  // Negative node id are reserved for test purposes.
        val system = ActorSystem("Node" + id)
        val acceptor = system.actorOf(Props(new Acceptor()))
        val learner = system.actorOf(Props(new Learner()))
        val communicator = system.actorOf(Communicator.props(Set(acceptor, learner), address, ipToIdMap, idToIpMap))
        nodes += (id -> NodeEntry(system, null, acceptor, learner, null, communicator))
      case _ =>
    }
  }

  def terminate(idToIpMap: IdToIpMap): Unit = {
    idToIpMap.foreach {
      case (id, _) if id >= 0 =>
        nodes(id).system.terminate()
      case _ =>
    }
  }

  def getActorSystem(nodeId: NodeId): Option[ActorSystem] = {
    nodes.get(nodeId) match {
      case Some(NodeEntry(system, _, _, _, _, _)) => Option(system)
      case _ => None
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
          case _ => None
        }
      case _ => None
    }
  }
}
