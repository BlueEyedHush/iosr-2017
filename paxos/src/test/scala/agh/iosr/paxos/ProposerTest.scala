package agh.iosr.paxos

import agh.iosr.paxos.actors.Proposer._
import agh.iosr.paxos.actors.{Proposer, Ready, ReceivedMessage}
import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.predef._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}


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
    testProposer = system.actorOf(Proposer.props(testLearner.ref, pNodeId, nodeCount, Some(testLogger.ref)))
    testLearner.expectMsg(LearnerSubscribe())
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

    /**
      * ToDo:
      * - rejects messages from older instances, but notices higher one and updates counte accordingly
      * - rejects messages from lower rounds than last initiated, but notices higher ones and adjusts his counter (even
      *   if round in progress, it just should not affect current round)
      * - for each case:
      *    - immediatelly after creation
      *    - after it becomes ready
      *    - after it entered phase 1
      *    - after it entered phase 2
      *   KvsGet causes (at some point) new round to be initiated (possibly also check order)
      * - takes proposer through full Paxos instance and monitors if reactions are correct
      *   - 1B contained different value - that value chosen, but then new Paxos instance initiated
      *   - 1B empty - progresses with his own value
      *   - no response to 1A -> retransmissions
      *   - no reponse to election result -> 2A retransmission
      *   - no retransmission to those that responded
      *   - higher id reported in 1B - abandon and start new instance
      *   - higher id response in 2B - wait patiently for voting results (should be-> immediatelly abandon instance,
      *     try new one)
      *   - handling of duplicate messages
      *     - multiple 1B and 2B from the same node should be ignored (but their values should be probably reported)
      *   - upon learning:
      *     - about success: new value taken
      *     - about failure: retrying with higher instance id
      *
      * ToDo v2:
      *  - helper (take actor to given state)
      *  - move textual logging to listener actor
      *
      */

  }

}
