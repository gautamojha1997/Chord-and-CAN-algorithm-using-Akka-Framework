package com.simulation

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import com.simulation.actors.can.BootstrapActor
import com.simulation.actors.can.BootstrapActor.createServerActorCAN
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

object CANActorDriver {

  private val conf = ConfigFactory.load("application.conf")

  val numUsers = conf.getInt("num_of_users")
  val numNodes = conf.getInt("num_of_nodes")

  val actorSystem = ActorSystem("actorSystem")

  val bootstrapActor = actorSystem.actorOf(Props(new BootstrapActor()),"bootstrap_actor")
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  var serverActorCount = 0

  def createServerNodeCAN(): Boolean = {
    if(numNodes > serverActorCount) {
      bootstrapActor ! createServerActorCAN()
      serverActorCount += 1
      return true
    }
    false
  }


  def loadData(id: Int): String = {
  ""
  }

  def getData(id: Int): Any = {
  }

  def printSnapshot(): Any = {
  }

}
