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
      val discovery = system.actorOf(Props[LearnersActorDiscovery])
      val actor = system.actorOf(Props(new LearnerActor(discovery)))
      actor ! LearnerSubscribe
    }
  }

  "333" must {
    "4444" in {
      val discovery = system.actorOf(Props[LearnersActorDiscovery])
      val actor = system.actorOf(Props(new LearnerActor(discovery)))
      actor ! ValueLearned(3, "String", 6)
    }
  }


  "555" must {
    "666" in {
      val discovery = system.actorOf(Props[LearnersActorDiscovery])
      val actor = system.actorOf(Props(new LearnerActor(discovery)))
      val subactor1 = system.actorOf(Props(new LearnerActor(discovery)))
      val subactor2 = system.actorOf(Props(new LearnerActor(discovery)))
      actor ! KvsGetRequest("GimmeKeys")
      expectMsg(KvsGetResponse(Some(3)))
    }
  }
}
