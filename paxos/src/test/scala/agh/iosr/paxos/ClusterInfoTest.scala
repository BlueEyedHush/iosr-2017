package agh.iosr.paxos

import agh.iosr.paxos.predef.IpAddress
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}

import scala.collection._

class ClusterInfoTest extends FreeSpec with Matchers {

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
    implicit val c = ConfigFactory.load("cluster-info-test.conf")

    "should return values matching those from file" - {
      import correct._

      "for myIp" in {
        val actualIp = ClusterInfo.myIpFromConf()
        actualIp shouldBe myIp
      }

      "for nodeMapping" in {
        val (actualIpToId, actualIdToIp) = ClusterInfo.nodeMapsFromConf()
        actualIdToIp shouldBe idToIp
        actualIpToId shouldBe ipToId
      }

    }


  }
}
