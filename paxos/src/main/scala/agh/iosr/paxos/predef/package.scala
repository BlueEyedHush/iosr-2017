package agh.iosr.paxos

package object predef {
  type InstanceId = Int

  type Value = Int
  val NULL_VALUE = Int.MinValue

  type RoundId = Int
  val NULL_ROUND = -1

  /**
    * NodeId's are global across the cluster, but this guarantee relies on uniform order in config
    */
  type NodeId = Int
  val NULL_NODE_ID = -1
}
