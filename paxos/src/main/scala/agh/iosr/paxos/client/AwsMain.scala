package agh.iosr.paxos.client

import java.io.PrintWriter

import com.typesafe.config.ConfigFactory

object AwsMain {
  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory.load()
    val tp = conf.getString("test_property")
    new PrintWriter("/tmp/paxos-test-file") { write(s"from config: $tp"); close }
  }
}
