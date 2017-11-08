package agh.iosr.paxos

import akka.actor._
import agh.iosr.paxos.Messages._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap
import scala.util.Random
import scala.concurrent.duration._

class LearnerActor(discovery:ActorRef) extends Actor {
  discovery ! RegisterLearner
  var subscribers = new ListBuffer[ActorRef]()
  var memory = HashMap[String, (InstanceId, Value)]()
  var getRequests = HashMap[Int, (ActorRef, String, ListBuffer[Option[(InstanceId, Value)]])]()
  val rand = Random

  override def receive = {
    case LearnerSubscribe =>
      subscribers += sender
    case x:ValueLearned =>
      memory.put(x.key, (x.when, x.value))
    case r:KvsGetRequest =>
      var requestKey = rand.nextInt
      while(getRequests.contains(requestKey)) requestKey = rand.nextInt

      getRequests += (requestKey -> (sender, r.key, new ListBuffer[Option[(InstanceId, Value)]]))
      discovery ! GiveMeLearners(requestKey)
    case r:KvsGetResponse =>
      println(r)
    case q:LearnerQuestionForValue =>
      sender ! LearnerAnswerWithValue(q.stomp, memory.get(q.key))
    case a:LearnerAnswerWithValue =>
      var reqData = getRequests.get(a.stomp)
      reqData match {
      case Some(_) =>
        reqData.get._3 += a.rememberedValue

      case None =>
        println("OVER")
      }
    case resp:LearnersListPlease =>
      for (actor <- resp.actors) {
        actor ! LearnerQuestionForValue(resp.requestKey, getRequests.get(resp.requestKey).get._2)
      }
      import scala.concurrent.ExecutionContext.Implicits.global
      context.system.scheduler.scheduleOnce(1 seconds, self, LearnerLoopback(resp.requestKey))
    case req:LearnerLoopback =>
      getRequests.get(req.requestKey).get._1 ! KvsGetResponse(Option(3))
  }
}
