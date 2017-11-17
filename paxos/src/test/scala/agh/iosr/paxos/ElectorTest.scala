package agh.iosr.paxos

import agh.iosr.paxos.actors.Elector._
import agh.iosr.paxos.actors._
import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.predef._
import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest._

case class MockDispatcher(val v: TestProbe) extends AnyVal

class ElectorTest extends TestKit(ActorSystem("MySpec"))
  with FreeSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfter {
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def create() = {
    val disProbe = TestProbe()
    val commProbe = TestProbe()
    val nodeId = 0
    val nodeCount = 1
    val elector = system.actorOf(Elector.props(disProbe.ref, nodeId, nodeCount))
    commProbe.send(elector, Ready)
    (elector, commProbe, disProbe)
  }


  "Elector" - {
    val key = "theValue"
    val value = 1
    val kv = KeyValue(key, value)

    "should accept KeyValue" in {
      implicit val (elector, comm, dis) = create
      elector ! value
      comm.expectMsg(SendMulticast(VoteForMe(value)))
    }

    "should accept wrapped KeyValue" in {
      implicit val (elector, comm, dis) = create
      comm.send(elector, ReceivedMessage(KvsSend(key, value), 0))
      comm.expectMsg(SendUnicast(KvsSend(key, value), 0))
    }

    "should handle keep alive" in {
      implicit val (elector, comm, dis) = create
      comm.send(elector, ReceivedMessage(KeepAlive(0), 1))
      dis.expectMsg(LeaderChanged(1))
    }

    "should handle voteForMe" in {
      implicit val (elector, comm, dis) = create
      comm.send(elector, ReceivedMessage(VoteForMe(0), 1))
      comm.expectMsg(SendUnicast(Vote(0), 1))
    }

    "should go into candidate" in {
      implicit val (elector, comm, dis) = create
      comm.send(elector, FollowerTimeout)
      comm.expectMsg(SendMulticast(VoteForMe(value)))
    }

    "should handle KeepAlive in candidate" in {
      implicit val (elector, comm, dis) = create
      comm.send(elector, FollowerTimeout)
      comm.expectMsg(SendMulticast(VoteForMe(value)))
      comm.send(elector, ReceivedMessage(KeepAlive(10), 1))
      dis.expectMsg(LeaderChanged(1))
    }

    "should handle VoteForMe in candidate" in {
      implicit val (elector, comm, dis) = create
      comm.send(elector, FollowerTimeout)
      comm.expectMsg(SendMulticast(VoteForMe(value)))
      comm.send(elector, ReceivedMessage(VoteForMe(10), 1))
      comm.expectMsg(SendUnicast(Vote(10), 1))
    }

    // "should become a leader" in {
    //   implicit val (elector, comm, dis) = create
    //   comm.send(elector, FollowerTimeout)
    //   comm.expectMsg(SendMulticast(VoteForMe(value)))
    //   comm.send(elector, ReceivedMessage(VoteForMe(1), 1))
    //   comm.expectMsg(SendUnicast(Vote(10), 1))
    //   dis.expectMsg(BecomingLeader)
    // }
  }
}
