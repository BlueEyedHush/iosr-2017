package agh.iosr.paxos

import akka.actor._
import agh.iosr.paxos.Messages._
import scala.collection.mutable.ListBuffer

class LearnerActor extends Actor {
  var subscribers = new ListBuffer[ActorRef]()
  var lastInstanceId: InstanceId = ???
  var lastKey: String = ???
  var lastValue: Value = ???

  override def receive = {
    case LearnerSubscribe =>
      subscribers += sender
    case x:ValueLearned =>
      println(x.when)
      println(x.key)
      println(x.value)
  }
}
