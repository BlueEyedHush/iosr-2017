package agh.iosr.paxos

import agh.iosr.paxos.Messages.PaxosMsg
import akka.util.ByteString

import scala.pickling._
import json._

object SerializationHelper {
  def serialize(msg: PaxosMsg): ByteString = ByteString(msg.pickle.value)

  def deserialize(bs: ByteString): PaxosMsg = bs.utf8String.unpickle[PaxosMsg]
}
