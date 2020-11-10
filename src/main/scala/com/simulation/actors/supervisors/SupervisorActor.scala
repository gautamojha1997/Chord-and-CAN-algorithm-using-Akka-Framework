package com.simulation.actors.supervisors
import akka.actor.{Actor, ActorSystem, Props}
import java.security.MessageDigest

import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor
import com.simulation.actors.servers.ServerActor.{initializeFingerTable, updateFingerTable}
import com.simulation.actors.supervisors.SupervisorActor.{createServerActor, getData}
import com.simulation.actors.users.UserActor.loadData
import com.simulation.beans.EntityDefinition

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SupervisorActor(id: Int, numNodes: Int) extends Actor{

  var nodes: mutable.Map[Int, String] = scala.collection.mutable.TreeMap[Int, String]()
  val system: ActorSystem = ActorSystem()
  val timeout = Timeout(10 seconds)


  override def receive: Receive = {

    case createServerActor(nodeCounter) => {
      val serverActor = system.actorOf(Props(new ServerActor(nodeCounter, numNodes)), "server_actor_" + nodeCounter)
      nodes += (nodeCounter -> serverActor.path.toString)
      nodeCounter += 1
      serverActor ! initializeFingerTable
      for ((k,v) <- nodes) {
          val serverActor = context.system.actorSelection("akka://actor-system/user/server_actor_"+k)
          serverActor ! updateFingerTable
      }
    }
    case getData(id) => {
      val serverActor = context.system.actorSelection("akka://actor-system/user/server_actor_0")
      val data = serverActor ? getData(id)
      val result = Await.result(data, timeout.duration)
      sender() ! result
    }
    // implement hashing function & load the data in appropriate node
    case loadData(data) => {
      val hashKey = md5(data.id.toString)
    }
  }

  def md5(s: String): Array[Byte] = { MessageDigest.getInstance("MD5").digest(s.getBytes) }
}

object SupervisorActor {
  case class createServerActor(serverActorCount : Int)
  case class getData(id: Int)
  case class loadData(data: EntityDefinition)
}

