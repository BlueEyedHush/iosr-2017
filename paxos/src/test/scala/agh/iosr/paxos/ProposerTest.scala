package agh.iosr.paxos

import agh.iosr.paxos.actors.{Proposer, Ready, ReceivedMessage, SendMulticast}
import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.predef._
import agh.iosr.paxos.utils.IdGenerator
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest._

/**
  * proposer should think it's running on node 0
  */
class ProposerTestHelper(val nodeCount: NodeId) {
  val PROPOSER_NODE_ID = 0

  def sendKvsGet(v: KeyValue)(implicit p: ActorRef) = {

  }

  def sendEmptyP1Bs()(implicit p: ActorRef, rid: RoundIdentifier) = ???
  def sendValuedP1Bs(pval: KeyValue)(implicit p: ActorRef, rid: RoundIdentifier) = ???
  def sendValueChosen(v: KeyValue)(implicit p: ActorRef, rid: RoundIdentifier) = ???

  def takeThroughWholeRound()(implicit p: ActorRef) = {
    val v = KeyValue("dummy", 1)
    sendKvsGet(v)
  }

  def expectNoMessage() = ???
  def expectToStartNewInstance() = ???

  def create(listener: Option[ActorRef] = None)(implicit system: ActorSystem) = {
    val learnerProbe = TestProbe()
    val commProbe = TestProbe()
    val proposer = system.actorOf(Proposer.props(learnerProbe.ref, PROPOSER_NODE_ID, nodeCount, listener))
    commProbe.send(proposer, Ready)
    (learnerProbe, commProbe, proposer)
  }
}

object CommTestHelper {
  def expectRoundStarted(v: KeyValue, comm: TestProbe) = {
    comm.expectMsgPF() {
      case SendMulticast(Prepare(_)) => true
    }
  }

  def expect2aWithValue(v: KeyValue, comm: TestProbe) = {
    comm.expectMsgPF() {
      case SendMulticast(AcceptRequest(_, v)) => true
    }
  }
}

class ProposerTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  import Proposer._

  val pNodeId: NodeId = 2
  val nodeCount: NodeId = 4
  var testProposer: ActorRef = _
  val testLearner: TestProbe = TestProbe()
  val testLogger: TestProbe = TestProbe()
  val testCommunicator: TestProbe = TestProbe()

  def communicateProposer(msg: Any): Unit = {
    testCommunicator.send(testProposer, msg)
  }

  val helper = new ProposerTestHelper(nodeCount)
  val startInstanceId = 0
  val startMoForNode0 = {
    val rid = new IdGenerator(0).nextId()
    RoundIdentifier(startInstanceId, rid)
  }

  override def beforeAll(): Unit = {
    testProposer = system.actorOf(Proposer.props(testLearner.ref, pNodeId, nodeCount, Some(testLogger.ref)))
    communicateProposer(Ready)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Proposer" - {

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

    "should subscribe to learner" in {
      val (probe, _, proposer) = helper.create()
      probe.expectMsg(LearnerSubscribe())
    }

    "should correctly register communicator" in {
      val (probe, comm, proposer) = helper.create()
      probe.expectMsg(CommInitialized(comm.ref))
    }

    "in phase 1" - {
      val ourValue = KeyValue("our", 1)
      implicit val mo = startMoForNode0

      "should initiate it after receiving request" in {
        val (logger, comm, proposer) = helper.create()

        helper.sendKvsGet(ourValue)
        CommTestHelper.expectRoundStarted(ourValue, comm)

        // testLogger.expectMsg(RequestReceived(KeyValue(key, value)))
        // testLogger.expectMsg(ContextChange("phase1"))
        // testLogger.expectMsgClass(classOf[PrepareSent])
      }

      "after receiving 1B" - {
        val previousValue = KeyValue("previous", 2)
        val testElements = List(helper.create(), helper.create())
        testElements.foreach {
          case (_, _, proposer) => helper.sendKvsGet(ourValue)
        }

        "empty" - {
          "should initiate 2A with requested value" in {
            val (logger, comm, proposer) = testElements(0)
            helper.sendEmptyP1Bs()
            CommTestHelper.expect2aWithValue(ourValue, comm)
          }
        }

        "containing value" - {
          "should initiate 2A with value already voted on" in {
            val (logger, comm, proposer) = testElements(1)
            helper.sendValuedP1Bs(previousValue)
            CommTestHelper.expect2aWithValue(previousValue, comm)
          }
        }
      }
    }

    "in case of message from different instance" - {
      // take him through a couple of rounds, only then check impact (use AutoPilot)
      "(lower one) should simply ignore it" in {
      }

      "(higher one) should take notice and use this information when starting new instance" in {

      }
    }

    /**
      * ToDo v2:
      * - take fully through the round
      * - helper (take actor to given state)
      * - move textual logging to listener actor
      *
      * ToDo:
      * - rejects messages from older instances, but notices higher ones and updates counte accordingly
      * - +learns about communicator
      * - +subscribes to learner
      * - all messages within round have correct MOs
      * - for each case:
      *    - immediatelly after creation
      *    - after it becomes ready
      *    - after it entered phase 1
      *    - after it entered phase 2
      *   KvsGet causes (at some point) new round to be initiated (possibly also check order)
      * - takes proposer through full Paxos instance and monitors if reactions are correct
      *   - +1B contained different value - that value chosen, but then new Paxos instance initiated
      *   - +1B empty - progresses with his own value
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
      *
      */

  }

}
