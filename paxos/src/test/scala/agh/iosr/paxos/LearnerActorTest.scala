package agh.iosr.paxos

import agh.iosr.paxos.actors._
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.predef._

import scala.concurrent.duration._

class LearnerActorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Learner actor" must {

    "successfully sent learned value to all subscribers" in {
      val testCommunicator = TestProbe()
      val actor = system.actorOf(Props(new Learner()))
      testCommunicator.send(actor, Ready)

      actor ! LearnerSubscribe()

      val instanceId = 3
      val key = "String"
      val value = 6
      testCommunicator.send(actor, ReceivedMessage(Accepted(MessageOwner(instanceId, NULL_ROUND), KeyValue(key, value)), NULL_NODE_ID))

      expectMsg(ValueLearned(instanceId, key, value))
    }

    "handle 'get' operation properly" in {
      val actor = system.actorOf(Props(new Learner()))
      val subActor1 = system.actorOf(Props(new Learner()))
      val subActor2 = system.actorOf(Props(new Learner()))

      val testCommunicator = TestProbe()
      val testCommunicator1 = TestProbe()
      val testCommunicator2 = TestProbe()

      testCommunicator.send(actor, Ready)
      testCommunicator1.send(subActor1, Ready)
      testCommunicator2.send(subActor2, Ready)

      testCommunicator.send(actor, ReceivedMessage(Accepted(MessageOwner(10, NULL_ROUND), KeyValue("someKey", 6)), NULL_NODE_ID))
      testCommunicator1.send(subActor1, ReceivedMessage(Accepted(MessageOwner(9, NULL_ROUND), KeyValue("someKey", 7)), NULL_NODE_ID))
      testCommunicator2.send(subActor2, ReceivedMessage(Accepted(MessageOwner(11, NULL_ROUND), KeyValue("someOtherKey", 8)), NULL_NODE_ID))

      actor ! KvsGetRequest("someKey")

      testCommunicator.receiveOne(1 seconds) match {
        case SendMulticast(msg) =>
          testCommunicator.send(actor, ReceivedMessage(msg, 1))
          testCommunicator1.send(subActor1, ReceivedMessage(msg, 1))
          testCommunicator2.send(subActor2, ReceivedMessage(msg, 1))
      }

      testCommunicator.receiveOne(1 seconds) match {
        case SendUnicast(msg, 1) => testCommunicator.send(actor, ReceivedMessage(msg, 1))
      }
      testCommunicator1.receiveOne(1 seconds) match {
        case SendUnicast(msg, 1) => testCommunicator1.send(actor, ReceivedMessage(msg, 2))
      }
      testCommunicator2.receiveOne(1 seconds) match {
        case SendUnicast(msg, 1) => testCommunicator2.send(actor, ReceivedMessage(msg, 3))
      }

      expectMsg(10 seconds, KvsGetResponse(Some(6)))
    }

    "not perform any action when down and continue work when waken up" in {
      val testCommunicator = TestProbe()
      val actor = system.actorOf(Props(new Learner()))
      testCommunicator.send(actor, Ready)

      actor ! LearnerSubscribe()

      val instanceId = 3
      val key = "String"
      val value = 6
      testCommunicator.send(actor, ReceivedMessage(FallAsleep, NULL_NODE_ID))
      testCommunicator.send(actor, ReceivedMessage(Accepted(MessageOwner(instanceId, NULL_ROUND), KeyValue(key, value)), NULL_NODE_ID))
      expectNoMessage(10 seconds)
      testCommunicator.send(actor, ReceivedMessage(WakeUp, NULL_NODE_ID))
      testCommunicator.send(actor, ReceivedMessage(Accepted(MessageOwner(instanceId, NULL_ROUND), KeyValue(key, value)), NULL_NODE_ID))
      expectMsg(ValueLearned(instanceId, key, value))
    }

  }
}
