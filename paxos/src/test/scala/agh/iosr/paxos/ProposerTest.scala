package agh.iosr.paxos

import agh.iosr.paxos.actors.ExecutionTracing.LogMessage
import agh.iosr.paxos.actors._
import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.messages.SendableMessage
import agh.iosr.paxos.predef._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest._
import org.slf4j.LoggerFactory

case class MockCommunicator(val v: TestProbe) extends AnyVal
case class MockLogger(val v: TestProbe) extends AnyVal

/**
  * proposer should think it's running on node 0
  */
class ProposerTestHelper(val nodeCount: NodeId) {
  /* small misrepresenation, made for convenience - messages should be sent by Communicator (TestProbe we
   * use in its place, but we  */
  import scala.concurrent.duration._

  val PROPOSER_NODE_ID: NodeId = 0
  private val SMALLEST_ROUND_ID: RoundId = -1 // unconditionally msmaller than anything IdGenerator'll generate
  private val others = (1 until nodeCount).toSeq
  private val DESIGNATED_OTHER: NodeId = others.last 

  private def sendOne(msg: SendableMessage)(implicit p: ActorRef, c: MockCommunicator) =
    c.v.send(p, ReceivedMessage(msg, DESIGNATED_OTHER))

  private def sendFromOthers(msg: SendableMessage, oList: Seq[NodeId] = others)(implicit p: ActorRef, c: MockCommunicator) =
    oList.foreach(id => c.v.send(p, ReceivedMessage(msg, id)))

  def sendKvsGet(v: KeyValue)(implicit p: ActorRef, c: MockCommunicator) =
    c.v.send(p, KvsSend(v.k, v.v))

  def sendEmptyP1Bs()(implicit p: ActorRef, currentRid: RoundIdentifier, c: MockCommunicator) =
    sendFromOthers(Promise(currentRid, SMALLEST_ROUND_ID, None))

  def sendValuedP1Bs(pval: KeyValue)(implicit p: ActorRef, currentRid: RoundIdentifier, c: MockCommunicator) =
    sendFromOthers(Promise(currentRid, SMALLEST_ROUND_ID, Some(pval)))

  def sendP1bRoundTooOld(pval: KeyValue)
                        (implicit p: ActorRef, currentRid: RoundIdentifier, c: MockCommunicator) = {
    sendFromOthers(RoundTooOld(currentRid, currentRid.roundId + 1))
  }

  def sendP1BsFrom(nodes: List[NodeId], v: Option[KeyValue])(implicit p: ActorRef, currentRid: RoundIdentifier, c: MockCommunicator) =
    sendFromOthers(Promise(currentRid, SMALLEST_ROUND_ID, v), nodes)


  def sendValueChosen(v: KeyValue)(implicit p: ActorRef, currentRid: RoundIdentifier, c: MockCommunicator) =
    c.v.send(p, ValueLearned(currentRid.instanceId, v.k, v.v))

  def sendP2bHigherProposalNack(optionId: Option[RoundId] = None)(implicit p: ActorRef, currentRid: RoundIdentifier, c: MockCommunicator) = {
    val rid = optionId.getOrElse(currentRid.roundId + 1)
    sendOne(HigherProposalReceived(currentRid, rid))
  }

  def sendP2bHigherProposalNack(fromWhom: List[NodeId])(implicit p: ActorRef, currentRid: RoundIdentifier, c: MockCommunicator) =
    sendFromOthers(HigherProposalReceived(currentRid, currentRid.roundId + 1), fromWhom)


  var actorId: Int = 0
  def create(name: String = "", disableTimeouts: Boolean = true)(implicit system: ActorSystem) = {
    val actorName = if (!name.isEmpty) name else {
      val numName = actorId.toString
      actorId += 1
      numName
    }

    val learnerProbe = TestProbe()
    val commProbe = TestProbe()
    val listener = TestProbe()
    val printer = system.actorOf(Printer.props(PROPOSER_NODE_ID), s"${actorName}_p")
    val proposer = system.actorOf(Proposer.props(learnerProbe.ref, PROPOSER_NODE_ID, nodeCount, Set(listener.ref, printer), disableTimeouts), actorName)
    commProbe.send(proposer, Ready)
    learnerProbe.expectMsg(LearnerSubscribe())
    (MockLogger(listener), MockCommunicator(commProbe), proposer)
  }

  def expectInstanceStarted(v: KeyValue, ridChecker: RoundIdentifier => Boolean = _ => true)(implicit comm: MockCommunicator): RoundIdentifier = {
    comm.v.expectMsgPF() {
      case SendMulticast(Prepare(rid)) if ridChecker(rid) => rid
    }
  }

  def expectNewInstanceStarted(v: KeyValue)(implicit currentRid: RoundIdentifier, comm: MockCommunicator): RoundIdentifier = {
    expectInstanceStarted(v, rid => rid.instanceId > currentRid.instanceId)
  }

  def expect2aWithValue(v: KeyValue)(implicit comm: MockCommunicator) = {
    comm.v.expectMsgPF() {
      case SendMulticast(AcceptRequest(_, value)) if v == value => true
    }
  }

  def expect2a()(implicit comm: MockCommunicator) = {
    comm.v.expectMsgPF() {
      case SendMulticast(AcceptRequest(_, _)) => true
    }
  }

  def fishForLoggerMsg(m: LogMessage)(implicit logger: MockLogger) = {
    logger.v.receiveWhile() {
      case message if message == m => true
    }
  }

  val noMesgWaitTime = 1 second

  def successfulVoting(v: KeyValue, sendValue: Boolean = true)(implicit p: ActorRef, c: MockCommunicator) = {
    if (sendValue)
      sendKvsGet(v)
    implicit val rid = expectInstanceStarted(v)
    sendEmptyP1Bs()
    expect2a()
    sendValueChosen(v)
    if (sendValue)
      c.v.expectNoMessage(noMesgWaitTime)
    rid
  }

  def successfulVoting1bTrip(v: KeyValue, alt: KeyValue, byNack: Boolean = false, sendValue: Boolean = true)(implicit p: ActorRef, c: MockCommunicator) = {
    /* first round */
    if (sendValue)
      sendKvsGet(v)
    implicit var rid = expectInstanceStarted(v)
    if(byNack)
      sendP1bRoundTooOld(alt)
    else {
      sendValuedP1Bs(alt)
      expect2a()
      sendValueChosen(alt)
    }
    /* second round */
    rid = expectInstanceStarted(v)
    sendEmptyP1Bs()
    expect2a()
    sendValueChosen(v)
    if (sendValue)
      c.v.expectNoMessage(noMesgWaitTime)
    rid
  }

  def successfulVoting2bTrip(v: KeyValue, alt: KeyValue, sendValue: Boolean = true)(implicit p: ActorRef, c: MockCommunicator) = {
    /* first round */
    if (sendValue)
      sendKvsGet(v)
    implicit var rid = expectInstanceStarted(v)
    sendEmptyP1Bs()
    expect2a()
    sendP2bHigherProposalNack()
    sendValueChosen(alt)
    /* second round */
    rid = expectInstanceStarted(v)
    sendEmptyP1Bs()
    expect2a()
    sendValueChosen(v)
    if (sendValue)
      c.v.expectNoMessage(noMesgWaitTime)
    rid
  }
}


class ProposerTest extends TestKit(ActorSystem("MySpec"))
  with FreeSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  val RETRANSMISSION_TIMEOUT = 200 // in ms
  val INSTANCE_TIMEOUT = 2000 // in ms
  val NODE_COUNT = 4

  import ExecutionTracing._

  val log = LoggerFactory.getLogger(classOf[ProposerTest])
  val helper = new ProposerTestHelper(NODE_COUNT)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Proposer" - {
    import shapeless.syntax.std.tuple._

    val ourValue = KeyValue("our", 1)
    val differentValue = KeyValue("previous", 2)

    "should correctly register communicator" in {
      implicit val (probe, comm, proposer) = helper.create("comm_test")
      probe.v.fishForSpecificMessage() {
        case CommInitialized(ref) if comm.v.ref == ref => true
      }
    }

    "in phase 1" - {
      "should initiate it after receiving request" in {
        implicit val (logger, comm, proposer) = helper.create("inst_start")

        helper.sendKvsGet(ourValue)
        helper.expectInstanceStarted(ourValue)
      }

      "after receiving 1B" - {
        def prepareActors(nameSuffix: String) = {
          implicit val r @ (logger, comm, proposer) = helper.create(s"after_1b_recv_$nameSuffix")
          helper.sendKvsGet(ourValue)
          val rid = helper.expectInstanceStarted(ourValue)
          r :+ rid
        }

        "empty with lower id" - {
          "should initiate 2A with requested value" in {
            implicit val (logger, comm, proposer, rid) = prepareActors("empty")
            helper.sendEmptyP1Bs()
            helper.expect2aWithValue(ourValue)
          }
        }

        "containing value with lower id" - {
          "should initiate 2A with value already voted on" in {
            implicit val (logger, comm, proposer, rid) = prepareActors("lower_id")
            helper.sendValuedP1Bs(differentValue)
            helper.expect2aWithValue(differentValue)
          }
        }

        "with higher id" - {
          "should abandon current instance and start new one with the same value" in {
            implicit val (logger, comm, proposer, rid) = prepareActors("higher_id")
            helper.sendP1bRoundTooOld(differentValue)
            helper.expectNewInstanceStarted(ourValue)
          }
        }
      }
    }

    "in phase 2" - {
      "when rejected in 2B" - {
        def prepareActor(nameSuffix: String) = {
          implicit val r @ (logger, comm, proposer) = helper.create(s"2b_reject_$nameSuffix")
          helper.sendKvsGet(ourValue)
          implicit val rid = helper.expectInstanceStarted(ourValue)
          helper.sendEmptyP1Bs()
          helper.expect2a()
          helper.sendP2bHigherProposalNack()
          r :+ rid
        }

        "but value gets chosen" - {
          "should report success" in {
            implicit val (logger: MockLogger, comm, proposer, rid: RoundIdentifier) = prepareActor("value_chosen")
            helper.sendValueChosen(ourValue)
            helper.fishForLoggerMsg(InstanceSuccessful(rid.instanceId))
          }
        }

        "and value wasn't chosen" - {
          "should start new round for the same value" in {
            implicit val (logger, comm, proposer, rid) = prepareActor("value_not_chosen")
            helper.sendValueChosen(differentValue)
            helper.fishForLoggerMsg(RequestProcessingStarted(ourValue))
          }
        }
      }
    }

    "should when timers expire" - {
      import scala.concurrent.duration._

      val rt = RETRANSMISSION_TIMEOUT
      val sent = List(1,2)
      val didntSent = List(3)

      def prepareActor(nameSuffix: String) = {
        implicit val r @ (logger, comm, proposer) = helper.create(s"timeout_$nameSuffix", false)
        helper.sendKvsGet(ourValue)
        implicit val rid = helper.expectInstanceStarted(ourValue)
        r :+ rid
      }

      "while waiting for 1Bs" - {
        "retransmit to those that didn't respond (and only those)" in {
          implicit val (logger, comm: MockCommunicator, proposer, rid) = prepareActor("1b")
          helper.sendP1BsFrom(sent, None)

          within (0.75*rt millis, 1.25*rt millis) {
            comm.v.expectMsgAllOf(didntSent.map(nid => SendUnicast(Prepare(rid), nid)) :_*)
          }
        }
      }

      "while waiting for vote result" - {
        def prepareActor1(name: String) = {
          implicit val r @ (logger, comm, proposer, rid) = prepareActor(name)
          helper.sendEmptyP1Bs()
          helper.expect2a()
          helper.sendP2bHigherProposalNack(sent)
          r
        }

        "retransmit to those that didn't respond" in {
          implicit val (_, comm: MockCommunicator, proposer, rid) = prepareActor1("2b")

          within (0.75*rt millis, 1.25*rt millis) {
            comm.v.expectMsgAllOf(didntSent.map(nid => SendUnicast(AcceptRequest(rid, ourValue), nid)) :_*)
          }
        }

        "abandon value if sufficiently long time elapses" in {
          implicit val (logger: MockLogger, _, proposer, rid) = prepareActor1("instance")

          within(0.75*INSTANCE_TIMEOUT millis, 1.25*INSTANCE_TIMEOUT millis) {
            logger.v.fishForMessage() {
              case TimeoutHit(TimeoutType.instance, _) => true
              case _ => false
            }
          }
        }
      }
    }

    "multi-instance test" - {
      val values = (1 until 5).map(x => KeyValue(x.toString, x))
      val altValue = KeyValue("alt", 100)

      "should be able to establish correct chain of values" - {

        type TestCb = KeyValue => (ActorRef, MockCommunicator) => RoundIdentifier
        def executeTest(name: String, tester: TestCb) = {
          implicit val r @ (logger, comm, proposer) = helper.create(name, disableTimeouts = false)
          values.zipWithIndex.foreach { case (v, idx) =>
            log.info(s"$name: round $idx started")
            val rid = tester(v)(proposer, comm)
            logger.v.receiveWhile() {
              case InstanceSuccessful(iid) if iid == rid.instanceId => true
            }
          }
        }

        "when starting with fresh instance" in {
          executeTest("chain_optimistic", (v) => (p,c) => helper.successfulVoting(v)(p,c))
        }

        "when entered instance that must be continued" in {
          executeTest("chain_pessimistic_1b_cont", (v) => (p,c) => helper.successfulVoting1bTrip(v, altValue)(p,c))
        }

        "when entered instance that's already in use" in {
          executeTest("chain_pessimistic_1b", (v) => (p,c) => helper.successfulVoting1bTrip(v, altValue, byNack = true)(p,c))
        }

        "when competing instance won in phase II" in {
          executeTest("chain_pessimistic_2b", (v) => (p,c) => helper.successfulVoting2bTrip(v, altValue)(p,c))
        }
      }

      "should continue with request queue processing without prompting" - {
        type TestCb = KeyValue => (ActorRef, MockCommunicator) => RoundIdentifier
        def executeTest(name: String, tester: TestCb) = {
          import scala.concurrent.duration._

          implicit val (logger, comm, proposer) = helper.create(name, disableTimeouts = false)

          // first send requests
          values.foreach(helper.sendKvsGet)

          // and only now start reponding to Paxos
          val rids = values.map(v => {
            log.info(s"new round")
            tester(v)(proposer, comm)
          })

          // ensure communicator gets no more messages (no more rounds)
          comm.v.expectNoMessage(1 second)

          // and ensure that votings were actually successful
          rids.foreach(rid => {
            logger.v.fishForSpecificMessage() {
              case InstanceSuccessful(iid) if iid == rid.instanceId => true
            }
          })
        }

        "in successful case" in {
          executeTest("no_prompt_opt", (v) => (p, c) => helper.successfulVoting(v, sendValue = false)(p,c))
        }

        "when must continue other instance" in {
          executeTest("no_prompt_1b_cont", (v) => (p,c) => helper.successfulVoting1bTrip(v, altValue, sendValue = false)(p,c))
        }

        "when rejected in 1b" in {
          executeTest("no_prompt_1b", (v) => (p,c) => helper.successfulVoting1bTrip(v, altValue, byNack = true, sendValue = false)(p,c))
        }

        "when overridden in 2b" in {
          executeTest("no_prompt_2b", (v) => (p,c) => helper.successfulVoting2bTrip(v, altValue, sendValue = false)(p,c))
        }
      }
    }


    /**
      * ToDo v2:
      * - take fully through the round
      * - helper (take actor to given state)
      * - move textual logging to listener actor
      * - look through logger if it responds consistently
      * - helper methods can have optional "senders" param, which defaults to all neighbours
      * - fix names so that they reflect we are mocking sending (expecially in helper)
      * - extract common stuff from fixtures
      * - merge helpers
      *
      * ToDo:
      * - rejects messages from older instances, but notices higher ones and updates counte accordingly
      * - +learns about communicator
      * - +subscribes to learner
      * - all messages within round have correct MOs
      * - for each case:
      *    - immediatelly after creation
      *    - after it becomes ready
      *    - after it entered phase 1
      *    - after it entered phase 2
      *   KvsGet causes (at some point) new round to be initiated (possibly also check order)
      * - takes proposer through full Paxos instance and monitors if reactions are correct
      *   - +1B contained different value - that value chosen, but then new Paxos instance initiated
      *   - +1B empty - progresses with his own value
      *   - + higher id reported in 1B - abandon and start new instance
      *   - +higher id response in 2B - wait patiently for voting results, then restart (should be-> immediatelly abandon instance,
      *     try new one)
      *   - +no response to 1A -> retransmissions
      *   - +no reponse to election result -> 2A retransmission
      *   - +no retransmission to those that responded
      *   - +instance timeout
      *   - handling of duplicate messages
      *     - multiple 1B and 2B from the same node should be ignored (but their values should be probably reported)
      *   - +upon learning:
      *     - +about success: new value taken
      *     - +about failure: retrying with higher instance id
    *     - comprehensive, expectMessagesAllOf test
      *
      *
      */

  }

}
