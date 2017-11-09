package agh.iosr.paxos

import java.net.InetSocketAddress

import scala.collection.immutable

package object predef {
  type InstanceId = Int

  type RoundId = Int
  val NULL_ROUND: RoundId = -1

  type Value = Int
  val NULL_VALUE: Value = Int.MinValue

  case class KeyValue(key: String, value: Int)
  val NULL_KEY_VALUE: KeyValue = KeyValue("", NULL_VALUE)

  /**
    * NodeId's are global across the cluster, but this guarantee relies on uniform order in config
    */
  type NodeId = Int
  val NULL_NODE_ID: NodeId = -1

  case object ConfigError extends RuntimeException

  type IpString = String

  type IdToIpMap = immutable.Map[NodeId, InetSocketAddress]
  type IpToIdMap = immutable.Map[InetSocketAddress, NodeId]

  case class MessageOwner(instanceId: InstanceId, roundId: RoundId)
}
