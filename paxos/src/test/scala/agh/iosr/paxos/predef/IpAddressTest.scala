package agh.iosr.paxos.predef

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class IpAddressTest extends FreeSpec with TableDrivenPropertyChecks with Matchers {
  val correctIps = Table(
    ("input", "expectedIp", "expectedPort"),
    ("192.168.112.211:1", "192.168.112.211", 1),
    ("1.2.3.4:1", "1.2.3.4", 1),
  )

  val incorrectIps = Table(
    ("ipAddress", "cause"),
    ("1.2.3:12", "missing octet"),
    ("192.168.112.24", "missing port"),
    ("192.168.112.24:", "port empty"),
  )

  "An IpAddress.fromString" - {
    "should succeed" - {
      forAll(correctIps) { (in, eip, ep) => {
        val r = IpAddress.fromString(in)

        r.isSuccess shouldBe true
        r.get.ip shouldBe eip
        r.get.port shouldBe ep

      }
      }
    }

    "should fail" - {
      forAll(incorrectIps) { (in, cause) => {
        val r = IpAddress.fromString(in)

        r.isSuccess shouldBe false
      }
      }

    }
  }

  "An IpAddress.toInetAddress" - {
    "should succeed" in {
      val ip = "192.168.1.12"
      val port = 1200

      val ina = IpAddress.fromString(s"$ip:$port").get.toInetAddress

      ina.getHostName.toString shouldBe ip
      ina.getPort shouldBe port

    }
  }
}
