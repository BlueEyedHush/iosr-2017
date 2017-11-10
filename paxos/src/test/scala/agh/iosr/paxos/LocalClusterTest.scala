package agh.iosr.paxos

import agh.iosr.paxos.actors.{Communicator, Ready, ReceivedMessage, SendUnicast}
import agh.iosr.paxos.messages.Messages.{AcceptRequest, Accepted, LearnerSubscribe, ValueLearned}
import agh.iosr.paxos.predef.{KeyValue, RoundIdentifier}
import agh.iosr.paxos.utils.{ClusterInfo, ElementNotFound, LocalClusterSetupManager}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class LocalClusterTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Learners and acceptors" must {

    "communicate with each other" in {
      implicit val config: Config = ConfigFactory.load("local-cluster-test.conf")

      val instanceId = 8
      val roundId = 4
      val key = "TestKey"
      val value = 14

      val myAddress = ClusterInfo.toInetSocketAddress("localhost:9992").get
      val (_, idToIp) = ClusterInfo.nodeMapsFromConf()
      val testIdToIp = Map(-99 -> myAddress)
      val combinedIdToIp = idToIp ++ testIdToIp
      val combinedIpToId = combinedIdToIp.map(_.swap)

      val manager = new LocalClusterSetupManager()
      manager.setup(idToIp, testIdToIp)

      val testSubscriber = TestProbe()
      val testCommunicator = system.actorOf(Communicator.props(Set(testSubscriber.ref), myAddress, combinedIpToId, combinedIdToIp))
      testSubscriber.expectMsg(Ready)

      val nodeId = 1
      manager.getNodeActor(nodeId, "learner") match {
        case Some(actorRef) => actorRef ! LearnerSubscribe()
        case None => throw ElementNotFound
      }

      val remoteId = 0
      testCommunicator ! SendUnicast(AcceptRequest(RoundIdentifier(instanceId, roundId), KeyValue(key, value)), remoteId)
      testSubscriber.expectMsg(ReceivedMessage(Accepted(RoundIdentifier(instanceId, roundId), KeyValue(key, value)), remoteId))

      expectMsg(ValueLearned(instanceId, key, value))

      manager.terminate(idToIp)
    }
  }

}
