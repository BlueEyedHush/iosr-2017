package agh.iosr.paxos

import java.util

import agh.iosr.paxos.Messages.{KvsGetRequest, KvsGetResponse, KvsSend}
import agh.iosr.paxos.predef.Key
import akka.actor.{Actor, ActorRef, Props}

import scala.collection.mutable

object Kvs {
  def props(learner: ActorRef, proposer: ActorRef): Props = Props(new Kvs(learner, proposer))
}

class Kvs(val learner: ActorRef, val proposer: ActorRef) extends Actor {
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
