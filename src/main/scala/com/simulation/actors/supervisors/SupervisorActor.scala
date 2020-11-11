package com.simulation.actors.supervisors
import akka.actor.{Actor, ActorSystem, Props}
import java.security.MessageDigest

import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor
import com.simulation.actors.servers.ServerActor.{initializeFingerTable, initializeFirstFingerTable, updateFingerTable, updateOthers}
import com.simulation.actors.supervisors.SupervisorActor.{createServerActor, getData}
import com.simulation.actors.users.UserActor.loadData
import com.simulation.beans.EntityDefinition

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

class SupervisorActor(id: Int, numNodes: Int) extends Actor{

  //var nodes: mutable.Map[Int, String] = scala.collection.mutable.TreeMap[Int, String]()
  var nodes: mutable.Map[String, Int] = scala.collection.mutable.HashMap[String, Int]()
  var set: mutable.TreeSet[Int] = scala.collection.mutable.TreeSet[Int]()
  val system: ActorSystem = ActorSystem()
  val timeout = Timeout(10 seconds)
  var hash:String = _


  override def receive: Receive = {

    case createServerActor(nodeCounter) => {
      val serverActor = system.actorOf(Props(new ServerActor(nodeCounter, numNodes)), "server_actor_" + nodeCounter)
      hash = md5(nodeCounter.toString, numNodes).mkString(",")
      //nodeCounter += 1
      if(nodes.nonEmpty){

        serverActor ! initializeFingerTable(hash, "akka://actor-system/user/server_actor_"+Random.between(0, set.size))
        serverActor ! updateOthers(nodeCounter)

        /*for ((k,v) <- nodes) {
          val serverActor = context.system.actorSelection("akka://actor-system/user/server_actor_"+k)
          serverActor ! updateFingerTable
        }*/
      }
      else
        serverActor ! initializeFirstFingerTable(hash)

      nodes += (serverActor.path.toString -> nodeCounter)
      set += nodeCounter
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

  def md5(s: String, bitsNumber: Int): String = {
    val md = java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
    val bin = BigInt(md, 16).toString(2).take(bitsNumber)
    Integer.parseInt(bin, 2).toString
  }
}

object SupervisorActor {
  case class createServerActor(serverActorCount : Int)
  case class getData(id: Int)
  case class loadData(data: EntityDefinition)
}


