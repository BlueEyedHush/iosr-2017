package agh.iosr.paxos

import akka.actor.Actor

class SampleActor extends Actor {
  override def receive = {
    case msg =>
      sender ! msg
  }
}
