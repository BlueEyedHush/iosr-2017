package agh.iosr.paxos

import agh.iosr.paxos.actors.{Communicator, Ready, ReceivedMessage, SendUnicast}
import agh.iosr.paxos.messages.Messages.{AcceptRequest, Accepted, LearnerSubscribe, ValueLearned}
import agh.iosr.paxos.predef.{IpToIdMap, KeyValue, RoundIdentifier}
import agh.iosr.paxos.utils.{ClusterInfo, ElementNotFound, LocalClusterSetupManager}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class LocalClusterTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Learners and acceptors" must {

    "communicate with each other" in {
        // @todo move much of that logic to manager

      val instanceId = 8
      val roundId = 4
      val key = "TestKey"
      val value = 14

      val clusterIdToIp: IpToIdMap = List(
        "localhost:2550",
        "localhost:2551",
        "localhost:2552",
      ).map(ClusterInfo.toInetSocketAddress(_).get).zipWithIndex.toMap
      val listener = ClusterInfo.toInetSocketAddress("localhost:9992").get

      val manager = new LocalClusterSetupManager(clusterIdToIp, listener)
      manager.setup()

      val (combinedIpToIdMap, combinedIdToIpMap) = manager.getCombinedMaps()
      val testSubscriber = TestProbe()
      val testCommunicator = system.actorOf(Communicator.props(Set(testSubscriber.ref), listener, combinedIpToIdMap, combinedIdToIpMap))
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

      manager.terminate()
    }
  }

}
