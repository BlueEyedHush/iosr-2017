package agh.iosr.paxos

import agh.iosr.paxos.actors.{Communicator, Ready, ReceivedMessage, SendUnicast}
import agh.iosr.paxos.messages.Messages.{AcceptRequest, Accepted, LearnerSubscribe, ValueLearned}
import agh.iosr.paxos.predef.{KeyValue, RoundIdentifier}
import agh.iosr.paxos.utils.{ClusterInfo, LocalClusterSetupManager}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
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

      val manager = new LocalClusterSetupManager()
      val (actualIpToId, actualIdToIp) = ClusterInfo.nodeMapsFromConf()
      val myAddress = ClusterInfo.toInetSocketAddress("localhost:9992").get
      val ipToId = actualIpToId + (myAddress -> -99)
      val idToIp = actualIdToIp + (-99 -> myAddress)

      manager.setup(idToIp)

      val testCommunicator = system.actorOf(Communicator.props(Set(self), myAddress, ipToId, idToIp))

      manager.getNodeActor(0, "learner") match {
        case Some(actorRef) => actorRef ! LearnerSubscribe()
        case None => throw new IllegalArgumentException()
      }

      testCommunicator ! SendUnicast(AcceptRequest(RoundIdentifier(instanceId, roundId), KeyValue(key, value)), 1)

      expectMsg(ValueLearned(instanceId, key, value))

      manager.terminate(idToIp)
    }
  }

}
