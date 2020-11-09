package com.simulation


import akka.actor.{ActorSystem, Props}
import com.simulation.actors.users.UserActor
import com.simulation.actors.users.UserActor.createUserActor
import com.typesafe.config.ConfigFactory

class ActorDriver {

  private val conf = ConfigFactory.load("application.conf")

  val numUsers = conf.getInt("num_of_users")
  val numNodes = conf.getInt("num_of_nodes")

  val actorSystem = ActorSystem("actorSystem")

  val serverActor = actorSystem.actorOf(Props(new ServerActor(1, numNodes)), "server_actor")
  val userActor = actorSystem.actorOf(Props(new UserActor(1, actorSystem)), "user_actor")
  val supervisorActor = actorSystem.actorOf(Props(new SupervisorActor(1, numNodes)),"supervisor_actor")


  (0 to numUsers).foreach {
    i => userActor ! createUserActor(i + 1)
  }




}
