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
import com.simulation.actors.users.UserActor.{createUserActor, getDataUserActor, loadDataUserActor}
import com.simulation.beans.EntityDefinition
import com.typesafe.config.ConfigFactory
import org.ddahl.rscala.RClient

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

object ActorDriver {

  private val conf = ConfigFactory.load("application.conf")

  val numUsers = conf.getInt("num_of_users")
  val numNodes = conf.getInt("num_of_nodes")

  val actorSystem = ActorSystem("actorSystem")

  val serverActor = actorSystem.actorOf(Props(new ServerActor(1, numNodes)), "server_actor")
  val userActor = actorSystem.actorOf(Props(new UserActor(1, actorSystem)), "user_actor")
  val supervisorActor = actorSystem.actorOf(Props(new SupervisorActor(1, numNodes)),"supervisor_actor")

  List.tabulate(numUsers)(i => userActor ! createUserActor(i))

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
   // val userActorId = RClientObj.evalD0("sample(%-, 1)",numUsers).toInt
    val userActorId = Random.nextInt(numUsers)
    val dataHandlerActor = actorSystem.actorSelection("akka://actorSystem/user/user_actor/"+userActorId)
    // fetch entity definition from id
    dataHandlerActor ! loadDataUserActor(data)
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
    val snapshotRetrieved = supervisorActor ? getSnapshot
    val result = Await.result(snapshotRetrieved, timeout.duration)
    result
  }
}
