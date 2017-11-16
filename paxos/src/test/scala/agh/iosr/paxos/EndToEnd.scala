package agh.iosr.paxos

import agh.iosr.paxos.actors._
import agh.iosr.paxos.predef._
import agh.iosr.paxos.utils._
import agh.iosr.paxos.messages.Messages._
import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Random

object EndToEnd {
  def main(args: Array[String]) {

    val numerOfNodes = 4
    val numberOfPuts = 3
    val sleepTime = 1000 //in milis

    var configs = Array.ofDim[Config](numerOfNodes)
    var managers = Array.ofDim[ClusterSetupManager](numerOfNodes)
    var ids = Array.ofDim[Int](numerOfNodes)

    for (i <- 0 until numerOfNodes) {
      configs(i) = ConfigFactory.load("end-to-end/conf" + i + ".conf");
      managers(i) = new ClusterSetupManager()
    }

    val (ipToId, idToIp) = ClusterInfo.nodeMapsFromSpecificConf(configs(0))

    for (i <- 0 until numerOfNodes) {
      ids(i) = managers(i).setup(idToIp, ClusterInfo.myIpFromSpecificConf(configs(i)))
    }

    var systems = Array.ofDim[ActorSystem](numerOfNodes)
    var kvses = Array.ofDim[ActorRef](numerOfNodes)

    for(i <- 0 until numerOfNodes) {
      systems(i) = managers(i).getActorSystem(ids(i)).get
      kvses(i) = managers(i).getNodeActor(ids(i), "kvStore").get
    }

    val random = new Random

    for(keyShuffleId <- 0 until numberOfPuts) {
      var idx = random.nextInt(numerOfNodes)

      var key = kvses(idx) + "!@#$%" + keyShuffleId
      var value = random.nextInt
      kvses(idx) ! KvsSend(key, value)
      Thread.sleep(sleepTime)
    }

    for (i <- 0 until numerOfNodes) {
      Await.result(systems(i).whenTerminated, Duration.Inf)
    }
  }
}
