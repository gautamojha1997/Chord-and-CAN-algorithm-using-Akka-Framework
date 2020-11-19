package com.simulation

import scala.language.postfixOps
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import com.simulation.actors.servers.ServerActor
import com.simulation.actors.supervisors.SupervisorActor
import akka.util.Timeout
import com.simulation.actors.supervisors.SupervisorActor.{createServerActor, getDataSupervisor, getSnapshot}
import com.simulation.actors.users.UserActor
import com.simulation.actors.users.UserActor.{createUserActor, getDataUserActor}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.{Data, FingerActor}
import com.typesafe.config.ConfigFactory
import org.ddahl.rscala.RClient
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.util.Random

object ActorDriver {

  private val conf = ConfigFactory.load("application.conf")

  val numUsers = conf.getInt("num_of_users")
  val numNodes = conf.getInt("num_of_nodes")

  val actorSystem = ActorSystem("actorSystem")

  val serverActor = actorSystem.actorOf(Props(new ServerActor(1, numNodes)), "server_actor")
  val userActor = actorSystem.actorOf(Props(new UserActor(1, actorSystem)), "user_actor")
  val supervisorActor = actorSystem.actorOf(Props(new SupervisorActor(1, numNodes, actorSystem)),"supervisor_actor")
  val fingerActor = actorSystem.actorOf(Props(new FingerActor()),"finger_actor")

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  List.tabulate(numUsers)(i => userActor ! createUserActor(i))

  val movieData = getMoviesData



  var serverActorCount = 0
 // val RClientObj = RClient()

  val timeout = Timeout(10 seconds)

  def createServerNode(): Boolean = {
    if(numNodes > serverActorCount) {
      supervisorActor ? createServerActor()
      serverActorCount += 1
      return true
    }
    false
  }

  def loadData(id: Int): String = {
    logger.info("In loadData driver")

   // val userActorId = RClientObj.evalD0("sample(%-, 1)",numUsers).toInt
    val userActorId = 1 //Random.nextInt(numUsers)
    val dataHandlerActor = actorSystem.actorSelection("akka://actorSystem/user/user_actor/"+userActorId)
    val resultFuture  = dataHandlerActor ? UserActor.loadData(movieData(id))
    val result = Await.result(resultFuture, timeout.duration)
    //dataHandlerActor ! UserActor.loadData(movieData(id))
    result.toString
  }

  def getData(id: Int): Any = {
    val dataHandlerActor = actorSystem.actorSelection("akka://actorSystem/user/user_actor/1")
    val dataRetrieved = dataHandlerActor ? getDataUserActor(id)
    val result = Await.result(dataRetrieved, timeout.duration)
    result
  }

  def printSnapshot(): Any = {
    logger.info("Print Snapshot Driver")
    val snapshotRetrieved = supervisorActor ? getSnapshot()
    val result = Await.result(snapshotRetrieved, timeout.duration)
    result
  }

  def getMoviesData: ListBuffer[EntityDefinition] = {
    val dataList: ListBuffer[EntityDefinition] = new ListBuffer[EntityDefinition]
    val lines = Source.fromResource("data.csv")
    var i: Int = 0
    for (line <- lines.getLines.drop(1)) {
      val cols = line.split(",")
      dataList += EntityDefinition(i, cols(0))
      i += 1
    }
    dataList
  }
}
