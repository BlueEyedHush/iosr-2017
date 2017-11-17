package agh.iosr.paxos.client

import agh.iosr.paxos.messages.Messages.KvsSend
import agh.iosr.paxos.utils.{ClusterInfo, ClusterSetupManager}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random


object LocalClient {

 def main(args: Array[String]) {
   val sleepTime = 1000 //in milis
   val valuesPerKey = 0.5 // It takes from 0 to 1, if 0 then one key will have many values; if valuesPerKey = 1, then almost every key will have one value

   implicit val config: Config = ConfigFactory.load()
   val (ipToId, idToIp) = ClusterInfo.nodeMapsFromConf()

   val manager = new ClusterSetupManager()
   val id = manager.setup(idToIp, ClusterInfo.myIpFromConf())
   val system = manager.getActorSystem(id).get
   val kvStore = manager.getNodeActor(id, "kvStore").get

   val random = new Random
   val keyPrefix = "" + this
   var keyShuffleId = 1 // can we assume, that if it overflows, it's okey?
   var value = 1

   while(true) {
     if (random.nextDouble < valuesPerKey) {
       keyShuffleId += 1
       value = 1
     } else {
       value += 1
     }

     val key = keyPrefix + "!@#$%" + keyShuffleId
     kvStore ! KvsSend(key, value)
     Thread.sleep(sleepTime)
   }

   Await.result(system.whenTerminated, Duration.Inf)
 }
}
