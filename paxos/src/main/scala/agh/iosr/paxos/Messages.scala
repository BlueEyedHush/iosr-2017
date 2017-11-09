package agh.iosr.paxos

trait SendableMessage

object Messages {
  import agh.iosr.paxos.predef._



  case class KvsSend(key: Key, value: Value)
  case class KvsGetRequest(key: Key)
  case class KvsGetResponse(value: Option[Value])

  // @todo cleanup this mess
  // @todo do we really need roundId in MessageOwner?


  class ConsensusMessage(val mo: MessageOwner) extends SendableMessage
  case class Prepare(_mo: MessageOwner) extends ConsensusMessage(_mo)
  case class Promise(_mo: MessageOwner, lastRoundVoted: RoundId, ov: Option[PaxosValue]) extends ConsensusMessage(_mo)
  case class AcceptRequest(_mo: MessageOwner, v: PaxosValue) extends ConsensusMessage(_mo)
  case class Accepted(_mo: MessageOwner, v: PaxosValue) extends ConsensusMessage(_mo)

  /** NACK for phase 1 */
  case class RoundTooOld(mo: MessageOwner, mostRecentKnown: InstanceId) extends SendableMessage
  /** NACK for phase 2 */
  case class HigherProposalReceived(mo: MessageOwner, roundId: RoundId) extends SendableMessage

  case class LearnerSubscribe()
  case class ValueLearned(when: InstanceId, key: Key, value: Value)
}


