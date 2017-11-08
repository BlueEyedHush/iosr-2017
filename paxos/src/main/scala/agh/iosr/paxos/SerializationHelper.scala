package agh.iosr.paxos

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.util.ByteString

object SerializationHelper {
  def serialize(msg: SendableMessage)(implicit as: ActorSystem): ByteString = {
    val se = SerializationExtension(as)
    val ba = se.serialize(msg)
    ByteString(ba.get)
  }

  def deserialize(bs: ByteString)(implicit as: ActorSystem): SendableMessage = {
    val se = SerializationExtension(as)
    val o = se.deserialize(bs.toArray, classOf[SendableMessage])
    o.get
  }
}
