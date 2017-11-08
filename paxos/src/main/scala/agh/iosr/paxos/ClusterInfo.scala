package agh.iosr.paxos


import agh.iosr.paxos.predef._
import akka.actor.{Actor, Props}
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection._
import scala.util.{Failure, Success}

case object GetInfo
case class NodeInfo(myIp: IpAddress,
                    ipToId: IpToIdMap,
                    idToIp: IdToIpMap)

object ClusterInfo {
  def props()(implicit config: Config) = Props(new ClusterInfo())
}

class ClusterInfo()(implicit config: Config) extends Actor {
  private val myIp = myIpFromConf()
  private val (ipToId, idToIp) = nodeMapsFromConf()
  
  override def receive = {
    case GetInfo =>
      sender ! NodeInfo(myIp, ipToId, idToIp)
  }



  private def myIpFromConf(): IpAddress = {
    IpAddress.fromString(config.getString("iosrPaxos.myAddress")) match {
      case Success(ip) => ip
      case Failure(t) => throw ConfigError
    }
  }

  private def nodeMapsFromConf(): (IpToIdMap, IdToIpMap) = {
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
