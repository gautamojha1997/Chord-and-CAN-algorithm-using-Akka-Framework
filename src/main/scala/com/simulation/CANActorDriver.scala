package com.simulation

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.can.BootstrapActor
import com.simulation.actors.can.BootstrapActor.{createServerActorCAN, getDataBootstrapCAN, getSnapshotCAN, loadDataBootstrapCAN, removeBootstrapNode}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.Utility.getMoviesData
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object CANActorDriver {

  private val conf = ConfigFactory.load("application.conf")

  val numUsers: Int = conf.getInt("num_of_users")
  val numNodes: Int = conf.getInt("num_of_nodes")

  val actorSystem: ActorSystem = ActorSystem("actorSystem")

  val bootstrapActor: ActorRef = actorSystem.actorOf(Props(new BootstrapActor(actorSystem)),"bootstrap_actor")
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  var serverActorCount = 0
  val movieData: ListBuffer[EntityDefinition] = getMoviesData
  val timeout: Timeout = Timeout(1000 seconds)


 // val shard = NodeActor.startMerchantSharding(actorSystem)

  def createServerNodeCAN(): Int = {
    logger.info("Add Node Driver")
    if(numNodes > serverActorCount) {
      bootstrapActor ! createServerActorCAN(serverActorCount)
      serverActorCount += 1
      return serverActorCount
    }
    -1
  }

  def loadData(id: Int): String = {
    logger.info("Load data Driver")
    val resultFuture  = bootstrapActor ? loadDataBootstrapCAN(movieData(id))
    val result = Await.result(resultFuture, timeout.duration)
    result.toString
  }

  def getData(id: Int): Any = {
    logger.info("Get data Driver")
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

  def removeNode(nodeIndex:Int): Boolean ={
    logger.info("Remove node Driver")
    bootstrapActor ! removeBootstrapNode(nodeIndex)
    true
  }

}
