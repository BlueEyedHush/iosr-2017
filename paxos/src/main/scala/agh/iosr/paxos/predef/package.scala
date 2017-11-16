package agh.iosr.paxos

import java.net.InetSocketAddress

import scala.collection.immutable

package object predef {
  type InstanceId = Int
  val NULL_INSTANCE_ID: InstanceId = -1

  type RoundId = Long
  val NULL_ROUND: RoundId = -1

  type Value = Int
  type Key = String
  case class KeyValue(k: Key, v: Value)

  /**
    * NodeId's are global across the cluster, but this guarantee relies on uniform order in config
    */
  type NodeId = Int
  val NULL_NODE_ID: NodeId = -1

  case object ConfigError extends RuntimeException

  type IpString = String

  type IdToIpMap = immutable.Map[NodeId, InetSocketAddress]
  type IpToIdMap = immutable.Map[InetSocketAddress, NodeId]

  case class RoundIdentifier(instanceId: InstanceId, roundId: RoundId)
}
