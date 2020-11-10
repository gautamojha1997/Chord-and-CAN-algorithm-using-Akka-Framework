package com.simulation


import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import com.simulation.actors.users.UserActor
import com.simulation.actors.users.UserActor.createUserActor
import com.typesafe.config.ConfigFactory
import org.ddahl.rscala.RClient

import scala.concurrent.Await

class ActorDriver {

  private val conf = ConfigFactory.load("application.conf")

  val numUsers = conf.getInt("num_of_users")
  val numNodes = conf.getInt("num_of_nodes")

  val actorSystem = ActorSystem("actorSystem")

  val serverActor = actorSystem.actorOf(Props(new ServerActor(1, numNodes)), "server_actor")
  val userActor = actorSystem.actorOf(Props(new UserActor(1, actorSystem)), "user_actor")
  val supervisorActor = actorSystem.actorOf(Props(new SupervisorActor(1, numNodes)),"supervisor_actor")


  (1 to numUsers).foreach {
    i => userActor ! createUserActor(i)
  }

  var serverActorCount = 0
  val RClientObj = RClient()

  val timeout = Timeout(10 seconds)

  def createServerNode(): Unit = {
    if(numNodes > serverActorCount) {
      serverActor ? createServerActor (serverActorCount)
      serverActorCount += 1
    }
  }

  def loadData(id: Int): Unit = {
    val userActorId = RClientObj.evalD0("sample(%-, 1)",numUsers).toInt
    val dataHandlerActor = actorSystem.actorSelection("akka://actorSystem/user/user_actor/"+userActorId)
    dataHandlerActor ! loadData(id)
  }

  def lookupData(id: Int): Any = {
    val userActorId = RClientObj.evalD0("sample(%-, 1)",numUsers).toInt
    val dataHandlerActor = actorSystem.actorSelection("akka://actorSystem/user/user_actor/"+userActorId)
    val dataRetrieved = dataHandlerActor ? lookupData(id)
    val result = Await.result(dataRetrieved, timeout.duration)
    result
  }

  def printSnapshot(): Any = {
    val snapshotRetrieved = supervisorActor ? getSnapshot
    val result = Await.result(snapshotRetrieved, timeout.duration)
    result
  }




}
