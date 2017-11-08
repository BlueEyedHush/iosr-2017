package agh.iosr.paxos

import java.net.InetSocketAddress

package object Messages {
  type Value = Int
  type InstanceId = Int
  type RoundId = Int

  case class MessageOwner(instanceId: InstanceId, roundId: RoundId)

  val NULL_ROUND: Int = -1
  val NULL_VALUE: Int = Int.MinValue

  trait PaxosMsg

  case class KvsSend(key: String, value: Value) extends PaxosMsg
  case class KvsGetRequest(key: String) extends PaxosMsg
  case class KvsGetResponse(value: Option[Value]) extends PaxosMsg

  case class Prepare(mo: MessageOwner) extends PaxosMsg
  case class Promise(mo: MessageOwner, mostRecentRoundVoted: RoundId, mostRecentValue: Value) extends PaxosMsg
  case class AcceptRequest(mo: MessageOwner, value: Value) extends PaxosMsg
  case class Accepted(mo: MessageOwner, value: Value) extends PaxosMsg

  /** NACK for phase 1 */
  case class RoundTooOld(mo: MessageOwner, mostRecentKnown: InstanceId) extends PaxosMsg
  /** NACK for phase 2 */
  case class HigherProposalReceived(mo: MessageOwner, roundId: RoundId) extends PaxosMsg

  case class LearnerSubscribe() extends PaxosMsg
  case class ValueLearned(when: InstanceId, key: String, value: Value) extends PaxosMsg

  case class SendUnicast(data: PaxosMsg, remote: InetSocketAddress)
  case class SendMulticast(data: PaxosMsg, destination: String)
  case class ReceivedMessage(data: PaxosMsg, remote: InetSocketAddress)
}

