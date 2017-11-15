package agh.iosr.paxos.utils

import agh.iosr.paxos.predef.NodeId
import akka.event.LoggingAdapter

object FileLog {
  def info(msg: Any)(implicit nodeId: NodeId, log: LoggingAdapter): Unit = {
    log.info("{} - {} - {}", System.currentTimeMillis, nodeId, msg)
  }
  def error(msg: Any)(implicit nodeId: NodeId, log: LoggingAdapter): Unit = {
    log.error("{} - {} - {}", System.currentTimeMillis, nodeId, msg)
  }
}
