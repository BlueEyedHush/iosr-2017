package agh.iosr.paxos


import java.net.InetSocketAddress
import java.util.regex.Pattern

import agh.iosr.paxos.predef._
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection._
import scala.util.{Failure, Success, Try}

case object InvalidAddressFormat extends Exception

object ClusterInfo {
  def myIpFromConf()(implicit config: Config): InetSocketAddress = {
    toInetSocketAddress(config.getString("iosrPaxos.myAddress")) match {
      case Success(ip) => ip
      case Failure(t) => throw ConfigError
    }
  }

  def nodeMapsFromConf()(implicit config: Config): (IpToIdMap, IdToIpMap) = {
    val mapBuilder = immutable.Map.newBuilder[InetSocketAddress, NodeId]

    val splitAddressStrings = config.getStringList("iosrPaxos.nodes")
      .asScala
      .map(toInetSocketAddress)

    if( splitAddressStrings.exists(_.isFailure) ) {
      // escalate
      throw ConfigError
    }

    mapBuilder.sizeHint(splitAddressStrings.size)
    splitAddressStrings.map(_.get).zipWithIndex.foreach { case (ip, id) => mapBuilder += (ip -> id)}

    val ipToId = mapBuilder.result()
    val idToIp = ipToId.map(_.swap)

    (ipToId, idToIp)
  }

  private val PORT_REGEX = s"[0-9]{1,5}"
  private val REGEX = Pattern.compile(s"(?<ip>.+):(?<port>$PORT_REGEX)")

  def toInetSocketAddress(str: String): Try[InetSocketAddress] = {
    val m = REGEX.matcher(str)
    if(m.matches()) {
      Try {
        new InetSocketAddress(m.group("ip"), m.group("port").toInt)
      }
    } else {
      Failure(InvalidAddressFormat)
    }
  }
}
