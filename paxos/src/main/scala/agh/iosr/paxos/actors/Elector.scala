package agh.iosr.paxos.actors
import java.util

import agh.iosr.paxos.messages.Messages._
import agh.iosr.paxos.predef._
import agh.iosr.paxos.utils.TimersManager
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.event.LoggingReceive

import scala.collection.mutable

/**
  * ~~~ I'm not sure if mechanism described below is correct, should be checked against some authoritative source ~~~
  *
  * ToDo (wip)
  * External:
  * - use in clients leader instead of proposer
  * Internal
  * - skeleton - states and messages
  *   - subscribe to learner
  *   - wait for communicator
  * - message demultiplexing to actors
  *   - must track instance id's passing by - it's his responsibility now
  * - Proposer refactoring
  *   - proposer per instance scheme
  *   - fast rounds support (adaption to leader election should be done 1st)
  *   - parent-child relationship informing that new instance must be started
  * - leader election mechanism
  *   - add to proposer needed capabilities
  *   - starting propsoer when needed
  *   - keepaliving (timer, reactions, sending)
  *   - reactions to Paxos messages (spawning special instance and forwarding message to it)
  * - logic for session preallocating (and proposer actor creation)
  * - set of messages for status logging
  *
  * New timers:
  * - LeaderTimeout - used in LeaderPresent to switch to LeaderAbsent
  * - LeaderKeepalive - used in Leader to broadcast keepalives (if needed)
  *   Although nodees treat any message from leader as keepalive, instead of monitoring what we send to whom, we
  *   probably should just send out keepalives on timer without any additional conditon
  *
  * New messages
  * - KeepAlive()
  * - messages for timers
  * - add to Paxos messages (or even Sendable message 'type' byte field)
  *
  * Using Paxos for leader election (alternative):
  * - handled by special Paxos instance, dedicated to this puprose
  * - whenever someone initiates election Paxos, anyone who has contact with leader could reject proposal
  *   but it's better to wait with response until next timeout comes (and take part if it doesn't)
  *   if after that we receive keepalive, let's send back HaveLeader (kind of NACK)
  * - we end up in situation of mulitple competing leaders - how to guarantee fast convergence?
  *   let's base ids on node numbers (counter x node_id) - but this alone doesn't guarantee fast convergence
  *   we could restrict everyone to be able to start only one round - I'm not sure if this won't lead to deadlock?
  *   maybe if we don't see voting result in ElectionTimeout, restart it?
  *
  * - we need to reconfigure slightly
  *   - we don't want to fail after getting single 1b rejection - only after receiving
  *     quorum of them
  * - how to mark messages
  *   - use dedicated range - it can be exhausted, reduces size of 'working' range
  *   - use single instance - what if message from previous instance wanders around a little and arrives during next one?
  *   - special type of messages (byte to distinguish message type?) - seems best
  * - what if consensus cannot be reached?
  *
  * - only monitor leader keepalives (and carry out normal operation); any message for a leader is treated as KeepAlive,
  *   but in absence of anything to communicate, leader sends KeepAlive
  * - When timer expires and keepalive was not seen, enter LeaderAbsent state. We also start from LeaderAbsent state
  *   - if we received before LeaderPaxosPrepare with higher ID, we can use him as our current leader
  * - in LeaderAbsent all operations are suspended, and we broadcast our Prepare
  * - proposers doesn't cache anything, any inflight requests are ignored (but they may still be voted in if their
  *   instance id's were higher than last one leader seen voted in)
  * - we don't accept proposals from none-leader nodes (and probably send them some info), but we continue to respond
  *   to coordinators of rounds started before leader has been selected
  *
  *   New leader initialization:
  *   - after becoming leader, we allocate a new range, higher than anything we've every seen _voted in_
  *
  * What happens to inflight requests during no-leader period?
  * - no leader -> no proposers to finish rounds he initiated
  * - new leader can either choose overlapping instance id range (to finish those requests, ranges should be small) or
  *   can choose entirely new range, putting the effort of retransmission on shoulder of clients
  *   (can he really? different value could mean that someone already overwrote it, we shouldn't try to overwrite it once
  *   again)
  *
  * Problem of client knowing whether his changes were saved or not?
  * - each request must have ID for deduplication purposes, which is included in the log
  * - so client can query cluster asking whether such request was voted in
  * - if cluster keeps track of the clients, this'll be most recent request voted in, so not much searching
  * - but where such data would be stored? on leader? what on leader failure? client's have to resubscribe?
  *
  *
  *
  * Rejected ideas:
  * - lets move enqueuing logic to Kvs (he already does it for KvsGet) - so Elector forwards Ready as soon as he
  *   receives one
  *   for it to be possible we should also introduce Ready message so that nothing is sent while we're doing something else
  *   doesn't make sense - Elector has multiple actors at his disposal, why he cannot use one of them?
  * - get communicator, learner, and acceptor (could get them from outside, but I don't think it can be done with
  *   propser, since Elector must be able to instantiate it for every new round he starts). But we could wire
  *   them inside companion object?
  *     Nah, in the initial variant let's change as little as possible - so only Proposers are under elector and
  *     he his equal to acceptor and learner (means he needs to subscribe to acceptor?)
  * - each node probably needs to keep in his queue messages that he sent out (or forwarded to proposer),
  *   and only remove them when he hears that they were chosen
  *   but this is exactly what client'd do if he cannot read his message from cluster, so it feels better to do it
  *   on best effort basis from the perspective of the cluster
  * - dedicated leader selection algorithm, described in a3365ebb2734839157c3f55fb7394820cd249560
  */

object Elector {
  def props(dispatcher: ActorRef, nodeId: NodeId, nodeCount: NodeId, disableTimeouts: Boolean = false): Props =
    Props(new Elector(dispatcher, nodeId, nodeCount, disableTimeouts))

  case object BecomingLeader
  case object LoosingLeader
  case class LeaderChanged(leaderId: NodeId)

  case object KeepAliveTick
  case object CandidateTimeout
  case object FollowerTimeout

  private val keepAliveConf = TimerConf("keepAlive", 200, KeepAliveTick)
  private val candidateTimeoutConf = RandomTimerConf("candidateTimeout", 300, 600, CandidateTimeout)
  private val followerTimeoutConf = RandomTimerConf("followerTimeout", 500, 800, FollowerTimeout)
}


class Elector(val dispatcher: ActorRef, val nodeId: NodeId, val nodeCount: NodeId, val disableTimeouts: Boolean) extends Actor with ActorLogging with Timers{

  import Elector._

  private val timerManager = TimersManager.getTimersManager(timers, disableTimeouts)
  private val requestQueue: util.List[KvsSend] = new util.LinkedList[KvsSend]
  private var communicator: ActorRef = _
  private var currentLeader: NodeId = _


  /**
    * + waiting for Ready (from communicator),
    * + enqueuing requests;
    * + after Ready -> follower
    */
  override def receive: Receive = {
    case Ready =>
      communicator = sender
      context.become(follower(0))

    case ReceivedMessage(KvsSend(key, value), _) =>
      requestQueue.add(KvsSend(key, value))
  }


  /**
    * + watching keepAlive,
    * + forwarding all requests to leader (use KvsSend),
    * + voting for candidates for a leader (max 1 vote per term, first candidate gains the vote)
    * + handling timer expiry (-> candidate)
    */
  def follower(currentTerm: InstanceId, _voted: Boolean = false): Receive = {
    timerManager.restartTimerOnce(followerTimeoutConf)
    var voted = _voted

    requestQueue.forEach(rq => communicator ! SendUnicast(rq, currentLeader))
    requestQueue.clear()

    LoggingReceive {
      case ReceivedMessage(KvsSend(key, value), _) =>
        communicator ! SendUnicast(KvsSend(key, value), currentLeader)

      case msg: KvsSend =>
        communicator ! SendUnicast(msg, currentLeader)

      case ReceivedMessage(KeepAlive(instanceId), remoteId) if instanceId == currentTerm =>
        if (currentLeader != remoteId) {
          currentLeader = remoteId
          dispatcher ! LeaderChanged(currentLeader)
        }
        timerManager.restartTimerOnce(followerTimeoutConf)

      case ReceivedMessage(KeepAlive(instanceId), remoteId) if instanceId > currentTerm =>
        if (currentLeader != remoteId) {
          currentLeader = remoteId
          dispatcher ! LeaderChanged(currentLeader)
        }
        context.become(follower(instanceId))

      case ReceivedMessage(VoteForMe(instanceId), remoteId) if instanceId == currentTerm && !voted =>
        communicator ! SendUnicast(Vote(instanceId), remoteId)
        voted = true
        timerManager.restartTimerOnce(followerTimeoutConf)

      case ReceivedMessage(VoteForMe(instanceId), remoteId) if instanceId > currentTerm =>
        communicator ! SendUnicast(Vote(instanceId), remoteId)
        context.become(follower(instanceId, _voted = true))

      case FollowerTimeout =>
        context.become(candidate(currentTerm + 1))
    }
  }


  /**
    * + attempting to become a leader,
    * + sending request for votes - VoteForMe,
    * + when received majority of votes in currentTerm -> becoming leader,
    * + upon receiving message for higher term -> fallback to follower,
    * + meanwhile enqueuing requests,
    * + handling timer expiry (-> candidate in next term)
    */
  def candidate(currentTerm: InstanceId): Receive = {
    timerManager.restartTimerOnce(candidateTimeoutConf)
    val votesGained: mutable.Set[NodeId] = mutable.Set.empty
    communicator ! SendMulticast(VoteForMe(currentTerm))
    LoggingReceive {
      case ReceivedMessage(KvsSend(key, value), _) =>
        requestQueue.add(KvsSend(key, value))

      case ReceivedMessage(KeepAlive(instanceId), remoteId) if instanceId >= currentTerm =>
        currentLeader = remoteId
        dispatcher ! LeaderChanged(currentLeader)
        timerManager.stopTimer(candidateTimeoutConf)
        context.become(follower(instanceId))

      case ReceivedMessage(VoteForMe(instanceId), remoteId) if instanceId > currentTerm =>
        communicator ! SendUnicast(Vote(instanceId), remoteId)
        timerManager.stopTimer(candidateTimeoutConf)
        context.become(follower(instanceId, _voted = true))

      case ReceivedMessage(Vote(instanceId), remoteId) if instanceId == currentTerm =>
        votesGained += remoteId
        if (votesGained.size > nodeCount / 2) {
          currentLeader = nodeId
          timerManager.stopTimer(candidateTimeoutConf)
          context.become(leader(instanceId))
        }

      case CandidateTimeout =>
        context.become(candidate(currentTerm + 1))
    }
  }


  /**
    * - enqueueing received requests (both local and network),  // Delegating to dispatcher
    * - reserving instances (cyclically), // Delegating to dispatcher
    * - keeping track of most recently started instances // Delegating to dispatcher
    * - spawning new PaxosInstance actors, // Delegating to dispatcher
    * + sending out keepalives on timer
    * + resigning from leader position upon message from higher term
    */
  def leader(currentTerm: InstanceId): Receive = {
    timerManager.restartTimerOnce(keepAliveConf)

    requestQueue.forEach(rq => dispatcher ! rq)
    requestQueue.clear()
    dispatcher ! BecomingLeader

    LoggingReceive {
      case ReceivedMessage(KvsSend(key, value), _) =>
        dispatcher ! KvsSend(key, value)

      case msg: KvsSend =>
        dispatcher ! msg

      case KeepAliveTick =>
        communicator ! SendMulticast(KeepAlive(currentTerm))
        timerManager.restartTimerOnce(keepAliveConf)

      case ReceivedMessage(KeepAlive(instanceId), _) if instanceId <= currentTerm =>
        communicator ! SendMulticast(KeepAlive(currentTerm))
        timerManager.restartTimerOnce(keepAliveConf)

      case ReceivedMessage(VoteForMe(instanceId), _) if instanceId <= currentTerm =>
        communicator ! SendMulticast(KeepAlive(currentTerm))
        timerManager.restartTimerOnce(keepAliveConf)

      case ReceivedMessage(KeepAlive(instanceId), remoteId) if instanceId > currentTerm =>
        currentLeader = remoteId
        dispatcher ! LoosingLeader
        dispatcher ! LeaderChanged(currentLeader)
        timerManager.stopTimer(keepAliveConf)
        context.become(follower(instanceId))

      case ReceivedMessage(VoteForMe(instanceId), remoteId) if instanceId > currentTerm =>
        communicator ! SendUnicast(Vote(instanceId), remoteId)
        dispatcher ! LoosingLeader
        timerManager.stopTimer(keepAliveConf)
        context.become(follower(instanceId, _voted = true))
    }
  }
}
