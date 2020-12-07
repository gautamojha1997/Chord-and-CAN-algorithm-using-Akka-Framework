package com.simulation

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.can.BootstrapActor
import com.simulation.actors.can.BootstrapActor.{createServerActorCAN, getDataBootstrapCAN, getSnapshotCAN, loadDataBootstrapCAN, removeBootstrapNode}
import com.simulation.utils.Utility.getMoviesData
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.language.postfixOps
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object CANActorDriver {

  private val conf = ConfigFactory.load("application.conf")

  val numUsers = conf.getInt("num_of_users")
  val numNodes = conf.getInt("num_of_nodes")

  val actorSystem = ActorSystem("actorSystem")

  val bootstrapActor = actorSystem.actorOf(Props(new BootstrapActor(actorSystem)),"bootstrap_actor")
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  var serverActorCount = 0
  val movieData = getMoviesData
  val timeout = Timeout(1000 seconds)

  def createServerNodeCAN(): Boolean = {
    if(numNodes > serverActorCount) {
      bootstrapActor ! createServerActorCAN(serverActorCount)
      serverActorCount += 1
      return true
    }
    false
  }

  def loadData(id: Int): String = {
    val resultFuture  = bootstrapActor ? loadDataBootstrapCAN(movieData(id))
    val result = Await.result(resultFuture, timeout.duration)
    result.toString
  }

  def getData(id: Int): Any = {
    val data = bootstrapActor ? getDataBootstrapCAN(id)
    val result = Await.result(data, timeout.duration)
    result
  }

  def printSnapshot(): Any = {
    logger.info("Print Snapshot Driver")
    val snapshotRetrieved = bootstrapActor ? getSnapshotCAN()
    val result = Await.result(snapshotRetrieved, timeout.duration)
    result
  }

  def removeNode(nodeIndex:Int): Any ={
    bootstrapActor ! removeBootstrapNode(nodeIndex)
  }

}
