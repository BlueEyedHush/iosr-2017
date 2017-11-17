package agh.iosr.paxos.actors

import java.util

import agh.iosr.paxos.messages.Messages.{KvsGetRequest, KvsGetResponse, KvsSend}
import agh.iosr.paxos.predef.Key
import akka.actor.{Actor, ActorRef, Props, ActorLogging}

import scala.collection.mutable

object Kvs {
  def props(learner: ActorRef, elector: ActorRef): Props = Props(new Kvs(learner, elector))
}

class Kvs(val learner: ActorRef, val elector: ActorRef) extends Actor with ActorLogging{
  private val requesters = mutable.Map[Key, util.LinkedList[ActorRef]]()

  override def receive = {
    case m @ KvsSend(k ,v) => elector ! m
      log.info("Kvs:" + self + " @ KvsSend")

    case m @ KvsGetRequest(k) =>
      log.info("Kvs:" + self + " @ KvsGetRequest")
      var alreadyQueried = true
      if (!requesters.contains(k)) {
        requesters += (k -> new util.LinkedList())
        alreadyQueried = false
      }

      requesters(k).add(sender())

      if(!alreadyQueried)
        learner ! m

    case m @ KvsGetResponse(k, _) =>
      log.info("Kvs:" + self + " @ KvsGetResponse")
      requesters(k).forEach(_ ! m)
      requesters.remove(k)
  }
}
