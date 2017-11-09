package agh.iosr.paxos

import agh.iosr.paxos.actors._
import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.predef._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class AcceptorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  var testAcceptor: ActorRef = _
  val testCommunicator: TestProbe = TestProbe()

  def communicateAcceptor(msg: Any): Unit = {
    testCommunicator.send(testAcceptor, msg)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll(): Unit = {
    testAcceptor = system.actorOf(Acceptor.props())
    communicateAcceptor(Ready)
  }

  "Acceptor" must {
    "promise to start participate in any round of unseen instance" in {
      val instanceId = 1
      val roundId = 2
      val remoteId = 3
      communicateAcceptor(ReceivedMessage(Prepare(MessageOwner(instanceId, roundId)), remoteId))
      testCommunicator.expectMsg(SendUnicast(Promise(MessageOwner(instanceId, roundId), NULL_ROUND, NULL_KEY_VALUE), remoteId))
    }

    "promise to start participate in a higher round of running instance" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 4
      communicateAcceptor(ReceivedMessage(Prepare(MessageOwner(instanceId, roundId)), remoteId))
      testCommunicator.expectMsg(SendUnicast(Promise(MessageOwner(instanceId, roundId), NULL_ROUND, NULL_KEY_VALUE), remoteId))
    }

    "repeat last promise of given instance to the same coordinator" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 4
      communicateAcceptor(ReceivedMessage(Prepare(MessageOwner(instanceId, roundId)), remoteId))
      testCommunicator.expectMsg(SendUnicast(Promise(MessageOwner(instanceId, roundId), NULL_ROUND, NULL_KEY_VALUE), remoteId))
    }

    "vote if it has not voted already or promised not to vote in given round of given instance" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 3
      val value = KeyValue("a", 10)
      communicateAcceptor(ReceivedMessage(AcceptRequest(MessageOwner(instanceId, roundId), value), remoteId))
      testCommunicator.expectMsg(SendMulticast(Accepted(MessageOwner(instanceId, roundId), value)))
    }

    "repeat the vote only if it is asked to vote for the same value in given round of given instance by the same coordinator" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 3
      val value = KeyValue("a", 10)
      communicateAcceptor(ReceivedMessage(AcceptRequest(MessageOwner(instanceId, roundId), value), remoteId))
      testCommunicator.expectMsg(SendMulticast(Accepted(MessageOwner(instanceId, roundId), value)))
    }

    "refuse to vote if it is asked to vote for some other value (key) than it already voted for in given round of given instance" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 3
      val value = KeyValue("x", 10)
      val highestRoundId = 5
      communicateAcceptor(ReceivedMessage(AcceptRequest(MessageOwner(instanceId, roundId), value), remoteId))
      testCommunicator.expectMsg(SendUnicast(HigherProposalReceived(MessageOwner(instanceId, roundId), highestRoundId), remoteId))
    }

    "refuse to vote if it is asked to vote for some other value (value) than it already voted for in given round of given instance" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 3
      val value = KeyValue("a", 8)
      val highestRoundId = 5
      communicateAcceptor(ReceivedMessage(AcceptRequest(MessageOwner(instanceId, roundId), value), remoteId))
      testCommunicator.expectMsg(SendUnicast(HigherProposalReceived(MessageOwner(instanceId, roundId), highestRoundId), remoteId))
    }

    "refuse to vote if it is asked to vote by some other coordinator in given round of given instance" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 8
      val value = KeyValue("a", 10)
      val highestRoundId = 5
      communicateAcceptor(ReceivedMessage(AcceptRequest(MessageOwner(instanceId, roundId), value), remoteId))
      testCommunicator.expectMsg(SendUnicast(HigherProposalReceived(MessageOwner(instanceId, roundId), highestRoundId), remoteId))
    }

    "refuse to vote if it promised not to vote in given round of given instance" in {
      val instanceId = 1
      val roundId = 4
      val remoteId = 3
      val value = KeyValue("a", 10)
      val highestRoundId = 5
      communicateAcceptor(ReceivedMessage(AcceptRequest(MessageOwner(instanceId, roundId), value), remoteId))
      testCommunicator.expectMsg(SendUnicast(HigherProposalReceived(MessageOwner(instanceId, roundId), highestRoundId), remoteId))
    }

    "inform about last vote in the promise of higher round of given instance" in {
      val instanceId = 1
      val roundId = 7
      val remoteId = 11
      val lastVoted = 5
      val vote = KeyValue("a", 10)
      communicateAcceptor(ReceivedMessage(Prepare(MessageOwner(instanceId, roundId)), remoteId))
      testCommunicator.expectMsg(SendUnicast(Promise(MessageOwner(instanceId, roundId), lastVoted, vote), remoteId))
    }

    "vote even if it has not received any prepare message for given instance" in {
      val instanceId = 8
      val roundId = 15
      val remoteId = 22
      val value = KeyValue("s", 15)
      communicateAcceptor(ReceivedMessage(AcceptRequest(MessageOwner(instanceId, roundId), value), remoteId))
      testCommunicator.expectMsg(SendMulticast(Accepted(MessageOwner(instanceId, roundId), value)))
    }

    "replay NAck to other coordinator trying to initiate already seen round of given instance" in {
      val instanceId = 1
      val roundId = 5
      val remoteId = 3
      val highestInstance = 8
      communicateAcceptor(ReceivedMessage(Prepare(MessageOwner(instanceId, roundId)), remoteId))
      testCommunicator.expectMsg(SendUnicast(RoundTooOld(MessageOwner(instanceId, roundId), highestInstance), remoteId))
    }

    "not perform any action when down and continue work when waken up" in {
      val instanceId = 20
      val roundId = 2
      val remoteId = 3
      communicateAcceptor(ReceivedMessage(FallAsleep, NULL_NODE_ID))
      communicateAcceptor(ReceivedMessage(Prepare(MessageOwner(instanceId, roundId)), remoteId))
      testCommunicator.expectNoMessage(10 seconds)
      communicateAcceptor(ReceivedMessage(WakeUp, NULL_NODE_ID))
      communicateAcceptor(ReceivedMessage(Prepare(MessageOwner(instanceId, roundId)), remoteId))
      testCommunicator.expectMsg(SendUnicast(Promise(MessageOwner(instanceId, roundId), NULL_ROUND, NULL_KEY_VALUE), remoteId))
    }

  }

}
