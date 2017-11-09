package agh.iosr.paxos

import agh.iosr.paxos.predef.{IdToIpMap, IpAddress, IpToIdMap}
import akka.actor.ActorSystem
import akka.io.{IO, Udp}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Matchers, WordSpecLike}

case class TestMessage() extends SendableMessage

class CommunicatorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers {

  def generatePrereq(clusterIps: List[IpAddress]) = {
    val ipToId: IpToIdMap = clusterIps.zipWithIndex.toMap
    val idToIp: IdToIpMap = ipToId.map(_.swap)
    (ipToId, idToIp)
  }


  "Connector" must {
    "unicast" must {
      val testActorIp = IpAddress.fromString("127.0.0.1:9692").get

      val unicastSet: List[IpAddress] = List(
        testActorIp,
        IpAddress("127.0.0.1", 9971),
      )

      val (ipToId, idToIp) = generatePrereq(unicastSet)
      val comm = system.actorOf(Communicator.props(Set(self), testActorIp, ipToId, idToIp))

      "forward incoming messages to master" in {
        val data = TestMessage()
        comm ! Udp.Received(SerializationHelper.serialize(data), null)
        expectMsg(ReceivedMessage(data, predef.NULL_NODE_ID))
      }

      "send unicast messages" in {
        IO(Udp) ! Udp.Bind(self, unicastSet(1).toInetAddress)
        expectMsg(Udp.Bound(unicastSet(1).toInetAddress))

        val data = TestMessage()
        comm ! SendUnicast(data, 1)
        expectMsg(Udp.Received(SerializationHelper.serialize(data), testActorIp.toInetAddress))
      }
    }


    "send multicast messages" in {
      val testActorIp = IpAddress.fromString("127.0.0.1:9693").get

      val multicastSet: List[IpAddress] = List(
        testActorIp,
        IpAddress("127.0.0.1", 9981),
        IpAddress("127.0.0.1", 9982),
        IpAddress("127.0.0.1", 9983),
      )

      val (ipToId, idToIp) = generatePrereq(multicastSet)
      val comm = system.actorOf(Communicator.props(Set(), testActorIp, ipToId, idToIp))

      val setLen = multicastSet.size
      multicastSet.slice(1, setLen).foreach {
        address =>
          IO(Udp) ! Udp.Bind(self, address.toInetAddress)
          expectMsg(Udp.Bound(address.toInetAddress))
      }

      val data = TestMessage()
      comm ! SendMulticast(data)

      val receivedMsg = Udp.Received(SerializationHelper.serialize(data), testActorIp.toInetAddress)
      multicastSet.slice(1, setLen).foreach {
        _ => expectMsg(receivedMsg)
      }

    }

  }

}
