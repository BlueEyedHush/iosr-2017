package agh.iosr.paxos

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import agh.iosr.paxos.Messages._
import scala.concurrent.duration._

class LearnerActorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Learner actor" must {
    "successfully sent learned value to all subscribers" in {
      val discovery = system.actorOf(Props[LearnersActorDiscovery])
      val actor = system.actorOf(Props(new LearnerActor(discovery)))
      actor ! LearnerSubscribe
      actor ! ValueLearned(3, "String", 6)
      expectMsg(ValueLearned(3, "String", 6))
    }
  }

  "Learner actor" must {
    "handle 'get' operation properly" in {
      val discovery = system.actorOf(Props[LearnersActorDiscovery])
      val actor = system.actorOf(Props(new LearnerActor(discovery)))
      val subactor1 = system.actorOf(Props(new LearnerActor(discovery)))
      val subactor2 = system.actorOf(Props(new LearnerActor(discovery)))
      val subactor3 = system.actorOf(Props(new LearnerActor(discovery)))

      actor ! ValueLearned(10, "someKey", 6)
      subactor1 ! ValueLearned(9, "someKey", 7)
      subactor2 ! ValueLearned(11, "someOtherKey", 8)
      actor ! KvsGetRequest("someKey")
      expectMsg(10 seconds, KvsGetResponse(Some(6)))
    }
  }
}
