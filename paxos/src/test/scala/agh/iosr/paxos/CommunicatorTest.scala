package agh.iosr.paxos

import java.net.InetSocketAddress

import agh.iosr.paxos.Messages._
import agh.iosr.paxos.predef.{IdToIpMap, IpAddress, IpToIdMap}
import akka.actor.{ActorRef, ActorSystem}
import akka.io.{IO, Udp}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

case class TestMessage() extends SendableMessage

class CommunicatorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  
  val address: String = "127.0.0.1"
  val port = 9692
  val testActorIp = IpAddress.fromString(f"$address:$port").get

  val addresses: List[IpAddress] = List(
      IpAddress("127.0.0.1", 9971),
      IpAddress("127.0.0.1", 9972),
      IpAddress("127.0.0.1", 9973),
  )

  val ipToId: IpToIdMap = addresses.zipWithIndex.toMap
  val idToIp: IdToIpMap = ipToId.map(_.swap)

  var comm: ActorRef = _

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll(): Unit = {
    comm = system.actorOf(Communicator.props(self, testActorIp, ipToId, idToIp), "communicator")
    // @todo remove sleep?
    Thread.sleep(100)
  }

  "Connector" must {

    "forward incoming messages to master" in {
      val data = TestMessage()
      val message = Udp.Received(SerializationHelper.serialize(data), null)
      comm ! message
      expectMsg(ReceivedMessage(data, null))
    }

    "send unicast messages" in {
      IO(Udp) ! Udp.Bind(self, addresses(2).toInetAddress)
      expectMsg(Udp.Bound(addresses(2).toInetAddress))

      val data = TestMessage()
      comm ! SendUnicast(data, addresses(2).toInetAddress)
      expectMsg(Udp.Received(SerializationHelper.serialize(data), new InetSocketAddress(address, port)))
    }
/*

    "send multicast messages" in {
      addresses.foreach {
        address =>
          IO(Udp) ! Udp.Bind(self, address.toInetAddress)
          expectMsg(Udp.Bound(address.toInetAddress))
      }

      val data = TestMessage()
      comm ! SendMulticast(data, "dummy")

      val receivedMsg = Udp.Received(SerializationHelper.serialize(data), new InetSocketAddress(address, port))
      addresses.foreach {
        _ => expectMsg(receivedMsg)
      }

    }
*/

  }

}
