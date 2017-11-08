package agh.iosr.paxos

package object predef {
  type InstanceId = Int

  type Value = Int
  val NULL_VALUE = Int.MinValue

  type RoundId = Int
  val NULL_ROUND = -1


  type NodeId = Int
  val NULL_NODE_ID = -1
}
