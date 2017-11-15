package agh.iosr.paxos.actors

import akka.actor.{Actor, ActorLogging, Props}

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
  * - Proposer refactoring
  * - leader election mechanism
  *   - adding tuning capabilities to refactored Proposer
  *   - starting propsoer when needed
  *   - keepaliving (timer, reactions, sending)
  *   - reactions to Paxos messages (spawning special instance and forwarding message to it)
  * - logic for session preallocating (and proposer actor creation)
  * - set of messages for status logging
  *
  * Our states here:
  * - receive - waiting for Ready, enqueuing requests; after Ready -> LeaderAbsent, start timer
  * - LeaderAbsent - seding out Prepare's, watching what others send (unitl ElectionTimer)
  * - LeaderPresent - watching keepalives, forwarding all requests to him (KvsSend, but wrapped in MessageReceived)
  * - Leader - reserving instances, spawning new PaxosInstance actors, enqueueing received requests (both local and network),
  *     sending out keepalives
  *
  * New timers:
  * - ElectionTimer - used in LeaderAbsent to terminate election
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
  * - we end up in situation of mulitple competing leaders - how to guarantee fast convergence?
  *   let's base ids on node numbers (counter x node_id) - highest proposal will win, overriding previous ones
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
  def props(): Props = Props(new Elector())
}

class Elector extends Actor with ActorLogging {
  override def receive = {

  }
}
