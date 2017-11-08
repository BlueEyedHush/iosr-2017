package agh.iosr.paxos

import java.net.InetSocketAddress

package object Messages {
  type Value = Int
  type InstanceId = Int
  type RoundId = Int

  case class MessageOwner(instanceId: InstanceId, roundId: RoundId)

  val NULL_ROUND: Int = -1
  val NULL_VALUE: Int = Int.MinValue

  trait SendableMessage

  case class KvsSend(key: String, value: Value)
  case class KvsGetRequest(key: String)
  case class KvsGetResponse(value: Option[Value])

  case class Prepare(mo: MessageOwner) extends SendableMessage
  case class Promise(mo: MessageOwner, mostRecentRoundVoted: RoundId, mostRecentValue: Value) extends SendableMessage
  case class AcceptRequest(mo: MessageOwner, value: Value) extends SendableMessage
  case class Accepted(mo: MessageOwner, value: Value) extends SendableMessage

  /** NACK for phase 1 */
  case class RoundTooOld(mo: MessageOwner, mostRecentKnown: InstanceId) extends SendableMessage
  /** NACK for phase 2 */
  case class HigherProposalReceived(mo: MessageOwner, roundId: RoundId) extends SendableMessage

  case class LearnerSubscribe()
  case class ValueLearned(when: InstanceId, key: String, value: Value)

  case class SendUnicast(data: SendableMessage, remote: InetSocketAddress)
  case class SendMulticast(data: SendableMessage, destination: String)
  case class ReceivedMessage(data: SendableMessage, remote: InetSocketAddress)
}

