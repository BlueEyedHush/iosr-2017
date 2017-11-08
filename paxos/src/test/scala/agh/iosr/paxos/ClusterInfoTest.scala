package agh.iosr.paxos

import agh.iosr.paxos.predef.IpAddress
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpecLike, Matchers}

import scala.collection._

class ClusterInfoTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender with FreeSpecLike with Matchers {
  object correct {
    val myIp = IpAddress("127.0.0.1", 2551)
    val clusterMembers = List(
      IpAddress("127.0.0.1", 2550),
      IpAddress("127.0.0.1", 2551),
    )

    val ipToId = immutable.Map(
      clusterMembers(0) -> 0,
      clusterMembers(1) -> 1,
    )

    val idToIp = immutable.Map(
      0 -> clusterMembers(0),
      1 -> clusterMembers(1),
    )
  }


  "ClusterInfo" - {
    "should return values matching those from file" in {
      import correct._

      implicit val c = ConfigFactory.load("cluster-info-test.conf")
      val actor = system.actorOf(ClusterInfo.props())

      actor ! GetInfo
      expectMsg(NodeInfo(myIp, ipToId, idToIp))
    }
  }
}
