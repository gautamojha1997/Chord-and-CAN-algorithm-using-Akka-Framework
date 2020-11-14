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
<<<<<<< HEAD
import com.simulation.actors.users.UserActor.createUserActor
import com.simulation.utils.Data
=======
import com.simulation.actors.users.UserActor.{createUserActor, getDataUserActor, loadDataUserActor}
import com.simulation.beans.EntityDefinition
>>>>>>> origin/master
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
  val supervisorActor = actorSystem.actorOf(Props(new SupervisorActor(1, numNodes)),"supervisor_actor")

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

  def loadData(id: Int): Unit = {
    logger.info("In loadData driver")
   // val userActorId = RClientObj.evalD0("sample(%-, 1)",numUsers).toInt
    val userActorId = Random.nextInt(numUsers)
    val dataHandlerActor = actorSystem.actorSelection("akka://actorSystem/user/user_actor/"+userActorId)
<<<<<<< HEAD
    dataHandlerActor ! UserActor.loadData(movieData(id))
=======
    // fetch entity definition from id
    dataHandlerActor ! loadDataUserActor(data)
>>>>>>> origin/master
  }

  def getData(id: Int): Any = {
    //val userActorId = RClientObj.evalD0("sample(%-, 1)",numUsers).toInt
    val userActorId = Random.nextInt(numUsers)
    val dataHandlerActor = actorSystem.actorSelection("akka://actorSystem/user/user_actor/"+userActorId)
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

  def getMoviesData: ListBuffer[Data] = {
    val dataList: ListBuffer[Data] = new ListBuffer[Data]
    val lines = Source.fromResource("data.csv")
    var i: Int = 0
    for (line <- lines.getLines.drop(1)) {
      val cols = line.split(",")
      dataList += Data(i, cols(0))
      i += 1
    }
    dataList
  }
}
