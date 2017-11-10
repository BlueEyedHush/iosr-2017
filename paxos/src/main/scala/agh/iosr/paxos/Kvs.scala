package agh.iosr.paxos

import java.util

import agh.iosr.paxos.Messages.{KvsGetRequest, KvsGetResponse, KvsSend}
import agh.iosr.paxos.predef.Key
import akka.actor.{Actor, ActorRef}
import com.typesafe.config.Config

import scala.collection.mutable

/**
  * Actor which wires everything together
  */
class Kvs(config: Config) extends Actor {
  private val (ipToId, idToIp) = ClusterInfo.nodeMapsFromConf()(config)
  private val nodeCount = ipToId.size
  private val myIp = ClusterInfo.myIpFromConf()(config)
  private val myId = ipToId(myIp)

  private val learner = context.actorOf(LearnerActor.props())
  private val proposer = context.actorOf(Proposer.props(learner, myId, nodeCount))
  private val acceptor = context.actorOf(Acceptor.props())
  private val communicator = context.actorOf(Communicator.props(Set(learner, proposer, acceptor), myIp, ipToId, idToIp))

  private val requesters = mutable.Map[Key, util.LinkedList[ActorRef]]()

  override def receive = {
    case m @ KvsSend(k ,v) => proposer ! m

    case m @ KvsGetRequest(k) =>
      var alreadyQueried = true
      if (!requesters.contains(k)) {
        requesters += (k -> new util.LinkedList())
        alreadyQueried = false
      }

      requesters(k).add(sender())

      if(!alreadyQueried)
        learner ! m

    case m @ KvsGetResponse(k, _) =>
      requesters(k).forEach(_ ! m)
      requesters.remove(k)
  }
}
