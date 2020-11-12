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
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

class SupervisorActor(id: Int, numNodes: Int) extends Actor{

  //var nodes: mutable.Map[Int, String] = scala.collection.mutable.TreeMap[Int, String]()
  var nodes: mutable.Map[String, Int] = scala.collection.mutable.HashMap[String, Int]()
  val system: ActorSystem = ActorSystem()
  val timeout = Timeout(10 seconds)
  var hash:String = _
  val nodeList = ListBuffer.range(0,numNodes)


  override def receive: Receive = {

    case createServerActor() => {
      val nodeIndex = nodeList(Random.nextInt(nodeList.size))
      val serverActor = system.actorOf(Props(new ServerActor(nodeIndex, numNodes)), "server_actor_" + nodeIndex)
      hash = md5(nodeIndex.toString, numNodes).mkString(",")
      if(nodes.nonEmpty){

        serverActor ! initializeFingerTable(hash, nodeIndex)
        serverActor ! updateOthers(nodeIndex)

        /*for ((k,v) <- nodes) {
          val serverActor = context.system.actorSelection("akka://actor-system/user/server_actor_"+k)
          serverActor ! updateFingerTable
        }*/
      }
      else
        serverActor ! initializeFirstFingerTable(hash)

      nodes += (serverActor.path.toString -> nodeIndex)
      nodeList -= nodeIndex
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
  case class createServerActor()
  case class getData(id: Int)
  case class loadData(data: EntityDefinition)
}


