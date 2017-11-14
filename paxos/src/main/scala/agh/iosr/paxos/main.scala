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
    implicit val config: Config = ConfigFactory.load("application.conf")
    val manager = new ClusterSetupManager()
    val (ipToId, idToIp) = ClusterInfo.nodeMapsFromConf()
    manager.setup(idToIp)

    val system = manager.getActorSystem(0).get

    var proposer = manager.getNodeActor(0, "proposer").get
    var acceptor = manager.getNodeActor(0, "acceptor").get
    var learner = manager.getNodeActor(0, "learner").get
    var kvStore = manager.getNodeActor(0, "kvStore").get
    var communicator = manager.getNodeActor(0, "communicator").get

    var myBestKey = "myBestKey"
    var myBestValue = 4
    kvStore ! KvsSend(myBestKey, myBestValue)


    Await.result(system.whenTerminated, Duration.Inf)
  }
}
