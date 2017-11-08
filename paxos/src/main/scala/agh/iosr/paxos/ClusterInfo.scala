package agh.iosr.paxos


import agh.iosr.paxos.predef._
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection._
import scala.util.{Failure, Success}

object ClusterInfo {
  def myIpFromConf()(implicit config: Config): IpAddress = {
    IpAddress.fromString(config.getString("iosrPaxos.myAddress")) match {
      case Success(ip) => ip
      case Failure(t) => throw ConfigError
    }
  }

  def nodeMapsFromConf()(implicit config: Config): (IpToIdMap, IdToIpMap) = {
    val mapBuilder = immutable.Map.newBuilder[IpAddress, NodeId]

    val convIp = config.getStringList("iosrPaxos.nodes")
      .asScala
      .map(IpAddress.fromString(_))

    if( convIp.exists(_.isFailure) ) {
      // escalate
      throw ConfigError
    }

    mapBuilder.sizeHint(convIp.size)
    convIp.map(_.get).zipWithIndex.foreach { case (ip, id) => mapBuilder += (ip -> id)}

    val ipToId = mapBuilder.result()
    val idToIp = ipToId.map(_.swap)

    (ipToId, idToIp)
  }
}
