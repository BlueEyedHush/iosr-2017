package agh.iosr.paxos.actors

import akka.actor.{Actor, ActorLogging, Props}

/**
  * ~~~ I'm not sure if mechanism described below is correct, should be checked against some authoritative source ~~~
  *
  * ToDo (wip)
  * - subscribe to learner
  * - elector get's communicator he's already subscribed to
  * - only KvsSend requests go to elector (just like to proposer before)
  * - leader election mechanism
  * - logic for session preallocating (and proposer actor creation)
  *
  * Our states here:
  * - receive - waiting for Ready, enqueuing requests; after Ready -> LeaderAbsent, start timer
  * - LeaderAbsent - seding out VoteRequests, watching what others send (unitl ElectionTimer)
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
  * - LeaderAnnoucement(leaderId) // leaderId not necessarily equals senderId
  * - messages for timers
  *
  * LeaderElection
  * - only monitor leader keepalives (and carry out normal operation); any message for a leader is treated as KeepAlive,
  *   but in absence of anything to communicate, leader sends KeepAlive
  * - When timer expires and keepalive was not seen, enter LeaderAbsent state. We also start from LeaderAbsent state
  *   - if we received before some LeaderAnnoucement with higher ID, we can use him as our current leader
  * - in LeaderAbsent all operations are suspended, and we broadcast our LeaderAnnoucement
  * - only store id of highest seen LeaderAnnoucement
  * - If, after timer expires, no higher ID request has been seen, we become leader
  * - proposers doesn't cache anything, any inflight requests are ignored (but they may still be voted in if their
  *   instance id's were higher than last one leader seen voted in)
  * - we don't accept proposals from none-leader nodes, but we continue to respond to coordinators of rounds started
  *   before leader has been selected
  * This mechanism seems to differ from what Raft uses, therfore it probably contains a mistake somewhere.
  *
  *   Messages:
  *   - send out LeaderAnnoucements, start election timer
  *   - received LeaderAnnoucement:
  *      - higher, no timeout: don't change leaders, but save back info about such an offer, send back NotYet
  *      - higher, timeout: means he sent out his own already, if better sends back LeaderAccepted
  *      - lower, no timeout: send back LeaderAnnoucement with ID of your leader
  *      - lower, timeout: send back LeaderAnnoucement with your own ID
  *   - no timeout happens in normal operation, timeout happens in LeaderAbsent state
  *   - what if during normal operation, leader receives higher ID request?
  *   - when timer expires, if no one has sent higher priority request, become leader
  *
  *   New leader initialization:
  *   - after becoming leader, we allocate a new range, higher than anything we've every seen _voted in_
  *
  *   In this leader election scheme is possible for nodes think diferent node is the leader. Why?
  *   - keepalive didn't reach node in time -> we can monitor rtts and adjust time -> different duration of timeout
  *     across nodes -> any message from node  treated as keepalive?
  *   It this a problem?
  *   Kind of - if node thinks someone else is the leader, it'll reject proposals from the real leader.
  *   But if he's not getting keepalives from node he thinks is the leader, reelection'll be triggered and real
  *   leader should be selected then.
  *   Could this other node think himself leader and send keepalives? This would require him to also miss current
  *   leader's annoucement (which can happen, e.g. in split brain scenario). Who is the actual leader would depend
  *   on who has the quorum.
  *   Possible solutions (but I don't think this is really a problem):
  *   - !build into leader mechanisms which triggers reelection if his proposals are constantly rejected
  *   - after seeing any message from other, higher ID node (even outside of election round), start treating him as
  *     leader (and reset leader timer) -> leader instability; but even in this case nodes can disagree (albeit for
  *     a shorter time)
  *   - Paxos vote the leader
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
  */

object Elector {
  def props(): Props = Props(new Elector())
}

class Elector extends Actor with ActorLogging {
  override def receive = {

  }
}
