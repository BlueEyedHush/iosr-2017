package agh.iosr.paxos.messages

trait SendableMessage

object Messages {
  import agh.iosr.paxos.predef._

  case class KvsSend(key: Key, value: Value)
  case class KvsGetRequest(key: Key)
  case class KvsGetResponse(key: Key, value: Option[Value])

  // @todo make this case object
  case class LearnerSubscribe()
  case class ValueLearned(when: InstanceId, key: String, value: Value)

  sealed trait ConsensusMessage extends SendableMessage {
    def mo: RoundIdentifier
  }

  object ConsensusMessage {
    def unapply(cm: ConsensusMessage): Option[RoundIdentifier] = Some(cm.mo)
  }

  case class Prepare(mo: RoundIdentifier) extends ConsensusMessage
  case class Promise(mo: RoundIdentifier, lastRoundVoted: RoundId, ov: Option[KeyValue]) extends ConsensusMessage
  case class AcceptRequest(mo: RoundIdentifier, v: KeyValue) extends ConsensusMessage
  case class Accepted(mo: RoundIdentifier, v: KeyValue) extends ConsensusMessage

  /** NACK for phase 1 */
  case class RoundTooOld(mo: RoundIdentifier, mostRecentKnown: RoundId) extends ConsensusMessage
  /** NACK for phase 2 */
  case class HigherProposalReceived(mo: RoundIdentifier, roundId: RoundId) extends ConsensusMessage

  case class LearnerQuestionForValue(requestId: Int, key: String) extends SendableMessage
  case class LearnerAnswerWithValue(requestId: Int, rememberedValue: Option[(InstanceId, Value)]) extends SendableMessage
  case class LearnerLoopback(requestId: Int)

  case object FallAsleep extends SendableMessage
  case object WakeUp extends SendableMessage
}
