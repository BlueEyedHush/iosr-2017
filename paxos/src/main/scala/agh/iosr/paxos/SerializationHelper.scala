package agh.iosr.paxos

import agh.iosr.paxos.Messages.SendableMessage
import akka.util.ByteString

import scala.pickling._
import scala.pickling.json._

object SerializationHelper {
  def serialize(msg: SendableMessage): ByteString = ByteString(msg.pickle.value)

  def deserialize(bs: ByteString): SendableMessage = bs.utf8String.unpickle[SendableMessage]
}
