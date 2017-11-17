package agh.iosr.paxos.utils

import java.util.concurrent.TimeUnit

import agh.iosr.paxos.predef.{RandomTimerConf, TimerConf}
import akka.actor.TimerScheduler

import scala.concurrent.duration.FiniteDuration

object TimersManager {
  def getTimersManager(timers: TimerScheduler, disableTimers: Boolean): TimersManager = {
    if (!disableTimers)
      return new TimersManager(timers)
    return new DummyTimersManager()
  }
}

class TimersManager(timers: TimerScheduler) {

  private val rand = scala.util.Random

  def startTimer(conf: TimerConf): Unit =
    timers.startPeriodicTimer(conf.key, conf.msg, FiniteDuration(conf.msInterval, TimeUnit.MILLISECONDS))

  def startTimerOnce(conf: TimerConf): Unit =
    timers.startSingleTimer(conf.key, conf.msg, FiniteDuration(conf.msInterval, TimeUnit.MILLISECONDS))

  def stopTimer(conf: TimerConf): Unit =
    timers.cancel(conf.key)

  def restartTimer(conf: TimerConf): Unit = {
    stopTimer(conf)
    startTimer(conf)
  }

  def restartTimerOnce(conf: TimerConf): Unit = {
    stopTimer(conf)
    startTimerOnce(conf)
  }

  def startTimerOnce(conf: RandomTimerConf): Unit = {
    val duration = conf.from + rand.nextInt(conf.to - conf.from)
    timers.startSingleTimer(conf.key, conf.msg, FiniteDuration(duration, TimeUnit.MILLISECONDS))
  }

  def stopTimer(conf: RandomTimerConf): Unit =
    timers.cancel(conf.key)

  def restartTimerOnce(conf: RandomTimerConf): Unit = {
    stopTimer(conf)
    startTimerOnce(conf)
  }
}

class DummyTimersManager extends TimersManager(null){

  override def startTimer(conf: TimerConf): Unit = {}

  override def startTimerOnce(conf: TimerConf): Unit = {}

  override def stopTimer(conf: TimerConf): Unit = {}

  override def restartTimer(conf: TimerConf): Unit = {}

  override def restartTimerOnce(conf: TimerConf): Unit = {}

  override def startTimerOnce(conf: RandomTimerConf): Unit = {}

  override def stopTimer(conf: RandomTimerConf): Unit = {}

  override def restartTimerOnce(conf: RandomTimerConf): Unit = {}

}
