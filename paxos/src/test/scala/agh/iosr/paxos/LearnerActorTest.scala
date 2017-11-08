package agh.iosr.paxos

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import agh.iosr.paxos.Messages._

class LearnerActorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "222" must {
    "1111" in {
      val actor = system.actorOf(Props[LearnerActor])
      actor ! LearnerSubscribe
    }
  }

  "333" must {
    "4444" in {
      val actor = system.actorOf(Props[LearnerActor])
      actor ! ValueLearned(3, "String", 6)
    }
  }
}
