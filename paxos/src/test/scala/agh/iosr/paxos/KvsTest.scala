package agh.iosr.paxos

import agh.iosr.paxos.Messages._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class KvsTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  var testKvs: ActorRef = _
  val testProposer: TestProbe = TestProbe()
  val testLearner: TestProbe = TestProbe()

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  before {
    testKvs = system.actorOf(Kvs.props(testLearner.ref, testProposer.ref))
  }
  
  "KeyValue Store" must {
    
    "pass PUT requests to its proposer" in {
      val request = KvsSend("testKey", 14)
      testKvs ! request
      testProposer.expectMsg(request)
    }

    "pass GET requests to its learner" in {
      val request = KvsGetRequest("testKey")
      testKvs ! request
      testLearner.expectMsg(request)
    }

    "not repeat active GET queries for given key" in {
      val request = KvsGetRequest("testKey")
      testKvs ! request
      testLearner.expectMsg(request)
      testKvs ! request
      testLearner.expectNoMessage(10 seconds)
    }

    "pass the response back to the requesters for given key" in {
      val request1 = KvsGetRequest("testKey")
      val request2 = KvsGetRequest("otherKey")
      val response = KvsGetResponse("testKey", Some(8))

      val requester1 = TestProbe()
      val requester2 = TestProbe()
      val requester3 = TestProbe()

      requester1.send(testKvs, request1)
      requester2.send(testKvs, request2)
      requester3.send(testKvs, request1)

      testLearner.expectMsg(request1)
      testLearner.expectMsg(request2)

      testLearner.send(testKvs, response)
      requester1.expectMsg(response)
      requester3.expectMsg(response)
      requester2.expectNoMessage(10 seconds)
    }

    "reset its state concerning given key upon receiving GET response" in {
      val request = KvsGetRequest("testKey")
      val response = KvsGetResponse("testKey", Some(8))
      testKvs ! request
      testLearner.expectMsg(request)
      testKvs ! request
      testLearner.expectNoMessage(10 seconds)
      testLearner.send(testKvs, response)
      expectMsg(response)
      testKvs ! request
      testLearner.expectMsg(request)
    }

  }

}
