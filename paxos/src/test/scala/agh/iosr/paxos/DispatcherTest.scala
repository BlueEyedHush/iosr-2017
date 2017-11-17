package agh.iosr.paxos

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

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

  "A dispatcher" - {
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
