package agh.iosr.paxos

import java.net.InetSocketAddress

import scala.collection.immutable

package object predef {
  type InstanceId = Int

  type Value = Int
  val NULL_VALUE = Int.MinValue

  type RoundId = Long
  val NULL_ROUND = -1

  /**
    * NodeId's are global across the cluster, but this guarantee relies on uniform order in config
    */
  type NodeId = Int
  val NULL_NODE_ID = -1

  case object ConfigError extends RuntimeException

  type IpString = String

  type IdToIpMap = immutable.Map[NodeId, InetSocketAddress]
  type IpToIdMap = immutable.Map[InetSocketAddress, NodeId]

  case class MessageOwner(instanceId: InstanceId, roundId: RoundId)
}
