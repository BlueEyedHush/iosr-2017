package agh.iosr.paxos


trait SendableMessage

object Messages {
  import agh.iosr.paxos.predef._

  case class KvsSend(key: String, value: Value)
  case class KvsGetRequest(key: String)
  case class KvsGetResponse(value: Option[Value])

  case class LearnerSubscribe()
  case class ValueLearned(when: InstanceId, key: String, value: Value)

  case class Prepare(mo: MessageOwner) extends SendableMessage
  case class Promise(mo: MessageOwner, mostRecentRoundVoted: RoundId, mostRecentValue: KeyValue) extends SendableMessage
  case class AcceptRequest(mo: MessageOwner, value: KeyValue) extends SendableMessage
  case class Accepted(mo: MessageOwner, value: KeyValue) extends SendableMessage

  /** NACK for phase 1 */
  case class RoundTooOld(mo: MessageOwner, mostRecentKnown: InstanceId) extends SendableMessage
  /** NACK for phase 2 */
  case class HigherProposalReceived(mo: MessageOwner, roundId: RoundId) extends SendableMessage

  case class LearnerQuestionForValue(requestId: Int, key: String) extends SendableMessage
  case class LearnerAnswerWithValue(requestId: Int, rememberedValue: Option[(InstanceId, Value)]) extends SendableMessage
  case class LearnerLoopback(requestId: Int)

  case object FallAsleep extends SendableMessage
  case object WakeUp extends SendableMessage
}
