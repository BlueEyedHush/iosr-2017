package agh.iosr.paxos

import agh.iosr.paxos.actors.Dispatcher.ProposerProvider
import agh.iosr.paxos.actors.DispatcherExecutionTracing.BatchAllocated
import agh.iosr.paxos.actors.Elector.BecomingLeader
import agh.iosr.paxos.actors.Proposer.Start
import agh.iosr.paxos.actors.{Dispatcher, ReceivedMessage}
import agh.iosr.paxos.messages.Messages.{Prepare, ValueLearned}
import agh.iosr.paxos.predef.{InstanceId, NodeId, RoundIdentifier}
import agh.iosr.paxos.utils.{MockCommunicator, MockLogger, MockProposer}
import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.collection._

object DispatcherTestHelper {
  val OUR_ID: NodeId = 0
  val CLUSTER_SIZE: NodeId = 4
  val INITIAL_IID: InstanceId = 0

  type IidMap = mutable.Map[InstanceId, MockProposer]

  class CachingProvider() {
    val iidToProbe: IidMap = mutable.Map[InstanceId, MockProposer]()

    val proposerP: ProposerProvider = (context, dispatcher, comm, iid, nodeId, nodeCount) => {
      // @todo pass system, not context?
      val probe = TestProbe()(context.system)
      iidToProbe += (iid -> MockProposer(probe))
      probe.ref
    }
  }

  def create()(implicit system: ActorSystem) = {
    val comm = TestProbe()
    val learner = TestProbe() // ignored, anyone can send learners values
    val logger = TestProbe()
    val cp = new CachingProvider()

    val props = Dispatcher.props(comm.ref, learner.ref, OUR_ID, CLUSTER_SIZE, Set(logger.ref), cp.proposerP)
    val actor = system.actorOf(props)
    (MockLogger(logger), MockCommunicator(comm), actor, cp)
  }
}

class DispatcherTest extends TestKit(ActorSystem("MySpec"))
  with FreeSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  /*
  ToDo:
  - regardless of whether leader or not, forwards all messages to correct Proposer
  - correct state transitions upon becoming leader
  - verify instances receive Start & KvsSend messages
  - when leader
    - restarts instances that fail for any reason
    - for each received KvsSend starts new instance
    - correctly allocates id batches
    - upon allocating new batch frees all unused from previous one
  - when follower
    - ignores failing instances
   */

  import agh.iosr.paxos.{DispatcherTestHelper => H}

  "A dispatcher" - {
    def iidRange(count: InstanceId) = {
      val first = H.INITIAL_IID
      val afterLast = first + count
      first until afterLast
    }

    def checkEachProposer[T](proposerCount: InstanceId)
                            (generator: InstanceId => List[T])
                            (verifier: (TestProbe, List[T]) => Unit) = {
      assert(proposerCount <= Dispatcher.batchSize)

      implicit val (MockLogger(logger), MockCommunicator(comm), dispatcher, cp) = H.create()
      val r = iidRange(proposerCount)

      dispatcher ! BecomingLeader

      val payloads = r.map(iid => generator(iid))
      val rWithIdx = r.zipWithIndex
      rWithIdx.foreach {
        case (iid, idx) => payloads(idx).foreach(m => dispatcher ! m)
      }

      logger.expectMsg(BatchAllocated())

      rWithIdx.foreach {
        case (iid, idx) => verifier(cp.iidToProbe(iid).v, payloads(idx))
      }
    }

    "starts proposers" in {
      checkEachProposer(5)(_ => List("dummy"))((p, _) => p.expectMsg(Start))
    }

    "should forward to proposers" - {
      "when leader" - {
        // @todo table drive for all message types
        // @todo test across batches
        "consensus message" in {
          def m(iid: InstanceId) = List(ReceivedMessage(Prepare(RoundIdentifier(iid, 12)), 0))
          checkEachProposer(5)(m)((p, ms) => p.expectMsgAllOf(Start, ms.head))
        }

        "ValueLearned" in {
          def m(iid: InstanceId) = List(ValueLearned(iid, "dummy", 1))
          checkEachProposer(5)(m)((p, ms) => p.expectMsgAllOf(Start, ms.head))
        }
      }

      "when follower" in {
        implicit val (MockLogger(logger), MockCommunicator(comm), dispatcher, cp) = H.create()
        dispatcher ! BecomingLeader
      }
    }

/*    "multi-instance test" - {
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
     }*/
  }

}
