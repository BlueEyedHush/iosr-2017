package agh.iosr.paxos.utils

import agh.iosr.paxos.messages.SendableMessage
import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.util.ByteString

class Serializer(implicit as: ActorSystem) {
  private val se = SerializationExtension(as)

  def serialize(msg: SendableMessage): ByteString = ByteString(se.serialize(msg).get)

  def deserialize(bs: ByteString): SendableMessage = se.deserialize(bs.toArray, classOf[SendableMessage]).get
}
