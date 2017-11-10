package agh.iosr.paxos

import java.net.InetSocketAddress

import agh.iosr.paxos.actors._
import agh.iosr.paxos.messages.SendableMessage
import agh.iosr.paxos.predef.{IdToIpMap, IpToIdMap}
import agh.iosr.paxos.utils.Serializer
import akka.actor.ActorSystem
import akka.io.{IO, Udp}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Matchers, WordSpecLike}

case class TestMessage() extends SendableMessage

class CommunicatorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers {

  private val serializer = new Serializer()
  
  def generatePrereq(clusterIps: List[InetSocketAddress]) = {
    val ipToId: IpToIdMap = clusterIps.zipWithIndex.toMap
    val idToIp: IdToIpMap = ipToId.map(_.swap)
    (ipToId, idToIp)
  }


  "Connector" must {
    "unicast" must {

      "inform subscribers when becomes ready to perform action" in {
        val testActorIp = new InetSocketAddress("localhost", 9692)
        val unicastSet: List[InetSocketAddress] = List(testActorIp, new InetSocketAddress("localhost", 9971))
        val (ipToId, idToIp) = generatePrereq(unicastSet)
        system.actorOf(Communicator.props(Set(self), testActorIp, ipToId, idToIp))
        expectMsg(Ready)
      }

      "forward incoming messages to master" in {
        val testActorIp = new InetSocketAddress("localhost", 9693)
        val unicastSet: List[InetSocketAddress] = List(testActorIp, new InetSocketAddress("localhost", 9971))
        val (ipToId, idToIp) = generatePrereq(unicastSet)
        val comm = system.actorOf(Communicator.props(Set(self), testActorIp, ipToId, idToIp))
        expectMsg(Ready)

        val data = TestMessage()
        comm ! Udp.Received(serializer.serialize(data), null)
        expectMsg(ReceivedMessage(data, predef.NULL_NODE_ID))
      }

      "send unicast messages" in {
        val testActorIp = new InetSocketAddress("localhost", 9694)
        val unicastSet: List[InetSocketAddress] = List(testActorIp, new InetSocketAddress("localhost", 9971))
        val (ipToId, idToIp) = generatePrereq(unicastSet)
        val comm = system.actorOf(Communicator.props(Set(self), testActorIp, ipToId, idToIp))
        expectMsg(Ready)

        IO(Udp) ! Udp.Bind(self, unicastSet(1))
        expectMsg(Udp.Bound(unicastSet(1)))

        val data = TestMessage()
        comm ! SendUnicast(data, 1)
        expectMsg(Udp.Received(serializer.serialize(data), testActorIp))
      }
    }


    "send multicast messages" in {
      val testActorIp = new InetSocketAddress("localhost", 9695)

      val multicastSet: List[InetSocketAddress] = List(
        testActorIp,
        new InetSocketAddress("localhost", 9981),
        new InetSocketAddress("localhost", 9982),
        new InetSocketAddress("localhost", 9983),
      )

      val (ipToId, idToIp) = generatePrereq(multicastSet)
      val comm = system.actorOf(Communicator.props(Set(), testActorIp, ipToId, idToIp))

      val setLen = multicastSet.size
      multicastSet.slice(1, setLen).foreach {
        address =>
          IO(Udp) ! Udp.Bind(self, address)
          expectMsg(Udp.Bound(address))
      }

      val data = TestMessage()
      comm ! SendMulticast(data)

      val receivedMsg = Udp.Received(serializer.serialize(data), testActorIp)
      multicastSet.slice(1, setLen).foreach {
        _ => expectMsg(receivedMsg)
      }

    }

  }

}
