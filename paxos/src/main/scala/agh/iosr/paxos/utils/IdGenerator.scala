package agh.iosr.paxos.utils

import agh.iosr.paxos.predef.{NodeId, RoundId}

case object IdGeneratorOverflow extends Exception

class IdGenerator(val nodeId: NodeId, val firstIdToReturn: Long = 0) {
  private var nid: Long = firstIdToReturn

  def nextId(): RoundId = {
    if(nid > Int.MaxValue) {
      throw IdGeneratorOverflow
    }

    var id: Long = nid
    id <<= 32
    id |= nodeId

    nid += 1

    id
  }
}
