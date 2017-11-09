package agh.iosr.paxos

import agh.iosr.paxos.Messages.{AcceptRequest, Accepted, LearnerSubscribe, ValueLearned}
import agh.iosr.paxos.predef.{KeyValue, MessageOwner}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class SmokeTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {


  "Learners and acceptors" must {

    "communicate with each other" in {
      implicit val config: Config = ConfigFactory.load("smoke-test.conf")

      val instanceId = 8
      val roundId = 4
      val key = "TestKey"
      val value = 14

      val manager = new NodesSetupManager()
      val (actualIpToId, actualIdToIp) = ClusterInfo.nodeMapsFromConf()
      val myAddress = ClusterInfo.toInetSocketAddress("localhost:9692").get
      val ipToId = actualIpToId + (myAddress -> -99)
      val idToIp = actualIdToIp + (-99 -> myAddress)

      manager.setup(idToIp)
      val testCommunicator = system.actorOf(Communicator.props(Set(self), myAddress, ipToId, idToIp))
      expectMsg(Ready)

      testCommunicator ! SendUnicast(LearnerSubscribe(), 0)
      testCommunicator ! SendUnicast(AcceptRequest(MessageOwner(instanceId, roundId), KeyValue(key, value)), 1)
      expectMsg(ReceivedMessage(Accepted(MessageOwner(instanceId, roundId), KeyValue(key, value)), 1))
      expectMsg(ReceivedMessage(ValueLearned(instanceId, key, value), 0))

      manager.terminate(idToIp)
    }
  }

}
