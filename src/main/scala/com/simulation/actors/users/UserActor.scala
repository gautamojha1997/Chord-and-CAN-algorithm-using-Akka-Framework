package com.simulation.actors.users

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.users.UserActor.{createUserActor, getData, loadData}
import com.simulation.beans.EntityDefinition

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class UserActor(userId: Int, actorSystem: ActorSystem) extends Actor{
  val timeout = Timeout(10 seconds)
  override def receive: Receive = {
    case loadData(data) =>
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")
      val nextActor = supervisorActor ? loadData(data)
      val result = Await.result(nextActor, timeout.duration)
      sender() ! result

    case getData(id) =>
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")
      val nextActor = supervisorActor ? getData(id)
      val result = Await.result(nextActor, timeout.duration)
      sender() ! result

    case createUserActor(id) =>
      val userActor = context.actorOf(Props(new UserActor(id, actorSystem)), "" + id)

  }
}

object UserActor {
  case class loadData(data:EntityDefinition)
  case class getData(id:Int)
  case class createUserActor(id:Int)
}