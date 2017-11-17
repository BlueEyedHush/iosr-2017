package agh.iosr.paxos.utils

import akka.testkit.TestProbe

case class MockCommunicator(val v: TestProbe) extends AnyVal
case class MockLogger(val v: TestProbe) extends AnyVal
case class MockDispatcher(val v: TestProbe) extends AnyVal
