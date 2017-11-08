package agh.iosr.paxos

import scala.collection.immutable

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

  case object ConfigError extends RuntimeException

  type IdToIpMap = immutable.Map[NodeId, IpAddress]
  type IpToIdMap = immutable.Map[IpAddress, NodeId]

  case class MessageOwner(instanceId: InstanceId, roundId: RoundId)
}
