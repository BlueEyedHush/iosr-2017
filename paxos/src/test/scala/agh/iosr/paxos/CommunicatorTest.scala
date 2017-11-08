package agh.iosr.paxos

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.io.{IO, Udp}

import agh.iosr.paxos.Messages.{KvsGetRequest, ReceivedMessage, SendMulticast, SendUnicast}

class CommunicatorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  val master: ActorRef = self
  val address: String = "localhost"
  val port = 9692
  val clusterAddresses: Map[String, List[InetSocketAddress]] = Map(
    "proposers" -> List(
      new InetSocketAddress("localhost", 9971),
      new InetSocketAddress("localhost", 9972),
      new InetSocketAddress("localhost", 9973)
    ),
    "acceptors" -> List(
      new InetSocketAddress("localhost", 9981),
      new InetSocketAddress("localhost", 9982),
      new InetSocketAddress("localhost", 9983)
    ),
    "learners" -> List(
      new InetSocketAddress("localhost", 9991),
      new InetSocketAddress("localhost", 9992),
      new InetSocketAddress("localhost", 9993)
    )
  )
  var comm: ActorRef = _

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll(): Unit = {
    comm = system.actorOf(Communicator.props(master, clusterAddresses, address, port), "communicator")
    Thread.sleep(100)
  }

  "Connector" must {

    "forward incoming messages to master" in {
      val data = KvsGetRequest("test")
      val message = Udp.Received(SerializationHelper.serialize(data), null)
      comm ! message
      expectMsg(ReceivedMessage(data, null))
    }

    "send unicast messages" in {
      IO(Udp) ! Udp.Bind(self, clusterAddresses("acceptors")(2))
      expectMsg(Udp.Bound(clusterAddresses("acceptors")(2)))

      val data = KvsGetRequest("test")
      comm ! SendUnicast(data, clusterAddresses("acceptors")(2))
      expectMsg(Udp.Received(SerializationHelper.serialize(data), new InetSocketAddress(address, port)))
    }

    "send multicast messages" in {
      val actorGroup = "learners"
      clusterAddresses(actorGroup).foreach {
        address =>
          IO(Udp) ! Udp.Bind(self, address)
          expectMsg(Udp.Bound(address))
      }

      val data = KvsGetRequest("test")
      comm ! SendMulticast(data, actorGroup)

      val receivedMsg = Udp.Received(SerializationHelper.serialize(data), new InetSocketAddress(address, port))
      clusterAddresses(actorGroup).foreach {
        _ => expectMsg(receivedMsg)
      }

    }

  }

}
