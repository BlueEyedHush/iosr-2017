package agh.iosr.paxos.client

import java.io.PrintWriter

object AwsMain {
  def main(args: Array[String]): Unit = {
    new PrintWriter("/tmp/paxos-test-file") { write("file contents"); close }
  }
}
