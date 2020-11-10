package com.simulation.actors.users

import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import com.simulation.actors.users.UserActor.{createUserActor, getData, loadData}

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

    case getData(data) =>
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")
      val nextActor = supervisorActor ? getData(data)
      val result = Await.result(nextActor, timeout.duration)
      sender() ! result

    case createUserActor(id) =>
      val userActor = context.actorOf(Props(new UserActor(id, actorSystem)), "" + id)

  }
}

object UserActor {
  case class loadData(data:)
  case class getData(data:)
  case class createUserActor(id:Int)
}