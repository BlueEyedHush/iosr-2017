package agh.iosr.paxos.utils

import akka.testkit.TestProbe

case class MockCommunicator(v: TestProbe) extends AnyVal
case class MockLogger(v: TestProbe) extends AnyVal
case class MockDispatcher(v: TestProbe) extends AnyVal
case class MockProposer(v: TestProbe) extends AnyVal
