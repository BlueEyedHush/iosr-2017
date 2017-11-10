package agh.iosr.paxos

import agh.iosr.paxos.actors._
import agh.iosr.paxos.utils._

import org.scalatest.{FreeSpec, Matchers}


class IdGeneratorTest extends FreeSpec with Matchers {
  "An IdGenerator" - {
    "should return correct values" in {
      val id = new IdGenerator(10, 50).nextId() // should return combination of nodeId 10 and localId 50
      id shouldBe 214748364800L + 10L
    }

    "should throw exception on overflow" in {
      val g = new IdGenerator(10, Int.MaxValue.toLong)
      // first one is returned without issues
      g.nextId() shouldBe 9223372032559808512L + 10L

      assertThrows[IdGeneratorOverflow.type] {
        // second should throw
        g.nextId()
      }
    }
  }
}
