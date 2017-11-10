package agh.iosr.paxos.utils

import agh.iosr.paxos.actors._
import agh.iosr.paxos.predef._
import agh.iosr.paxos.utils._
import agh.iosr.paxos.messages.Messages._
import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.Await


object Main {
  def main(args: Array[String]) {
    println("START")
    implicit val config: Config = ConfigFactory.load("main-cluster.conf")
    val manager = new ClusterSetupManager()
    val (ipToId, idToIp) = ClusterInfo.nodeMapsFromConf()
    manager.setup(idToIp)

    val system1 = manager.getActorSystem(0).get
    val system2 = manager.getActorSystem(1).get

    var p1 = manager.getNodeActor(0, "proposer").get
    var a1 = manager.getNodeActor(0, "acceptor").get
    var l1 = manager.getNodeActor(0, "learner").get
    var k1 = manager.getNodeActor(0, "kvStore").get
    var c1 = manager.getNodeActor(0, "communicator").get

    var p2 = manager.getNodeActor(1, "proposer").get
    var a2 = manager.getNodeActor(1, "acceptor").get
    var l2 = manager.getNodeActor(1, "learner").get
    var k2 = manager.getNodeActor(1, "kvStore").get
    var c2 = manager.getNodeActor(1, "communicator").get

    var myBestKey = "myBestKey"
    var myBestValue = 4
    k1 ! KvsSend(myBestKey, myBestValue)


    Await.result(system1.whenTerminated, Duration.Inf)
    Await.result(system2.whenTerminated, Duration.Inf)
  }
}
