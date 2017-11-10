package agh.iosr.paxos

import agh.iosr.paxos.Messages._
import agh.iosr.paxos.Proposer._
import agh.iosr.paxos.predef._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ProposerTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  val pNodeId: NodeId = 2
  val nodeCount: NodeId = 4
  var testProposer: ActorRef = _
  val testLearner: TestProbe = TestProbe()
  val testLogger: TestProbe = TestProbe()
  val testCommunicator: TestProbe = TestProbe()

  def communicateProposer(msg: Any): Unit = {
    testCommunicator.send(testProposer, msg)
  }

  override def beforeAll(): Unit = {
    testProposer = system.actorOf(Proposer.props(testLearner.ref, pNodeId, nodeCount, testLogger.ref))
    testLearner.expectMsg(LearnerSubscribe)
    communicateProposer(Ready)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }


  "Proposer" must {

    "initiate phase1 upon revceiving request" in {
      val key = "testKey"
      val value = 14
      val request = KvsSend(key, value)
      testProposer ! request
      testLogger.expectMsg(RequestReceived(KeyValue(key, value)))
      testLogger.expectMsg(ContextChange("phase1"))
      testLogger.expectMsgClass(classOf[PrepareSent])
    }

    "queue new requests while conducting phase1" in {
      val key = "otherKey"
      val value = 4
      val request = KvsSend(key, value)
      testProposer ! request
      testLogger.expectMsg(RequestQueued(KeyValue(key, value)))
    }

    "recognise Promise duplicates" in {
      val remoteId = 1
      val instanceId = 1
      val roundId = pNodeId
      val lastVotedRound = 0
      val lastVotedValue = None
      val promise = Promise(RoundIdentifier(instanceId, roundId), lastVotedRound, lastVotedValue)
      communicateProposer(ReceivedMessage(promise, remoteId))
      communicateProposer(ReceivedMessage(promise, remoteId))

      testLogger.expectMsg(PromiseDuplicate(promise))
    }

  }

}
