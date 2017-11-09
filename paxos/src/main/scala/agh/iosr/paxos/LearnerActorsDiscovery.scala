package agh.iosr.paxos

import akka.actor._
import agh.iosr.paxos.Messages._
import scala.collection.mutable.ListBuffer

class LearnersActorDiscovery extends Actor {
  var registeredLearners = new ListBuffer[ActorRef]()

  override def receive = {
    case RegisterLearner =>
      if (!registeredLearners.contains(sender)) registeredLearners += sender
    case x:GiveMeLearners =>
      sender ! LearnersListPlease(x.requestKey, registeredLearners.toList)
  }
}
