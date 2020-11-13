package com.simulation.actors.supervisors
import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor
import com.simulation.actors.servers.ServerActor.{initializeFingerTable, initializeFirstFingerTable, updateFingerTable, updateOthers}
import com.simulation.actors.supervisors.SupervisorActor.{createServerActor, getData}
import com.simulation.actors.users.UserActor.loadData
import com.simulation.beans.EntityDefinition
import com.simulation.utils.ApplicationConstants

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random
import scala.language.postfixOps


class SupervisorActor(id: Int, numNodes: Int) extends Actor{

  var nodesActorMapper: mutable.Map[Int, Int] = mutable.HashMap[Int, Int]()
  val system: ActorSystem = ActorSystem()
  val timeout = Timeout(10 seconds)
  //check if inclusive
  val unexploredNodes = ListBuffer.range(0,numNodes)
  var activeNodes: mutable.TreeSet[Int] = _


  override def receive: Receive = {

    case createServerActor() => {
      val nodeIndex = unexploredNodes(Random.nextInt(unexploredNodes.size))
      val serverActor = system.actorOf(Props(new ServerActor(nodeIndex, numNodes)), "server_actor_" + nodeIndex)
      if(activeNodes.nonEmpty){

        serverActor ! initializeFingerTable(nodeIndex)
        serverActor ! updateOthers(nodeIndex)

        /*for ((k,v) <- nodes) {
          val serverActor = context.system.actorSelection("akka://actor-system/user/server_actor_"+k)
          serverActor ! updateFingerTable
        }*/
      }
      else
        serverActor ! initializeFirstFingerTable(nodeIndex)

      unexploredNodes -= nodeIndex
      activeNodes += nodeIndex
    }

    case getData(id) => {
      val nodeIndex = nodesActorMapper.get(id)
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + nodeIndex)
      val data = serverActor ? getData(id)
      val result = Await.result(data, timeout.duration)
      sender() ! result
    }

    // implement hashing function & load the data in appropriate node
    case loadData(data) => {
      val nodeIndex = activeNodes.toVector(Random.nextInt(activeNodes.size))
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + nodeIndex)
      nodesActorMapper += (data.id -> nodeIndex)
      serverActor ! loadData(data)
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
  case class getSnapshot()
}


