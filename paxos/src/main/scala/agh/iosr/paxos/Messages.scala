package agh.iosr.paxos

package object Messages {
  type Value = Int
  type InstanceId = Int
  type RoundId = Int

  case class MessageOwner(instanceId: InstanceId, roundId: MessageOwner)

  val NULL_ROUND = -1
  val NULL_VALUE = Int.MinValue

  case class KvsSend(key: String, value: Value)
  case class KvsGetRequest(key: String)
  case class KvsGetResponse(value: Option[Value])

  case class Prepare(mo: MessageOwner)
  case class Promise(mo: MessageOwner, mostRecentRoundVoted: RoundId, mostRecentValue: Value)
  case class AcceptRequest(mo: MessageOwner, value: Value)
  case class Accepted(mo: MessageOwner, value: Value)

  /** NACK for phase 1 */
  case class RoundTooOld(mo: MessageOwner, mostRecentKnown: InstanceId)
  /** NACK for phase 2 */
  case class HigherProposalReceived(mo: MessageOwner, roundId: RoundId)

  case class LearnerSubscribe()
  case class ValueLearned(when: InstanceId, key: String, value: Value)
}
