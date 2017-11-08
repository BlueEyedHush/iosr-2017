package agh.iosr.paxos.predef

import org.scalatest.{FreeSpec, Matchers}

class IpAddressTest extends FreeSpec with Matchers {
  "An IpAddress.fromString" - {
    "for IP (4x3,1)" - {
      val r = IpAddress.fromString("192.168.112.211:1")

      "should succeed" in {
        assert(r.isSuccess)
      }

      "should return correct IP" in {
        r.get.ip shouldBe "192.168.112.211"
      }

      "should return correct port" in {
        r.get.port shouldBe 1
      }
    }

    "for IP (4x1,1)" - {
      val r = IpAddress.fromString("1.2.3.4:1")

      "should succeed" in {
        assert(r.isSuccess)
      }

      "should return correct IP" in {
        r.get.ip shouldBe "1.2.3.4"
      }

      "should return correct port" in {
        r.get.port shouldBe 1
      }
    }

    "for malformed IP" - {
      val r = IpAddress.fromString("192.168.112:1")

      "should fail" in {
        assert(r.isFailure)
      }
    }

    "for port missing" - {
      val r = IpAddress.fromString("192.168.112.24")

      "should fail" in {
        assert(r.isFailure)
      }
    }

    "for port empty" - {
      val r = IpAddress.fromString("192.168.112:")

      "should fail" in {
        assert(r.isFailure)
      }
    }
  }
}
