package com.simulation.actors.supervisors

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor
import com.simulation.actors.servers.ServerActor.{getDataServer, getSnapshotServer, initializeFingerTable, initializeFirstFingerTable,loadDataServer, updateOthers}
import com.simulation.actors.supervisors.SupervisorActor.{createServerActor, getDataSupervisor, getSnapshot, loadDataSupervisor}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.ApplicationConstants
import com.simulation.utils.Utility.md5

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
  var activeNodes: mutable.TreeSet[Int] = new mutable.TreeSet[Int]()


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
      activeNodes.add(nodeIndex)
      print(2)
    }

    case getDataSupervisor(id) => {
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + 0)
      val data = serverActor ? getDataServer(id,0)
      val result = Await.result(data, timeout.duration)
      sender() ! result
    }

    // implement hashing function & load the data in appropriate node
    case loadDataSupervisor(data) => {
      val hash = md5(data.id.toString, numNodes)
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + activeNodes.maxBefore(hash))
      serverActor ! loadDataServer(data)
    }

    case getSnapshot() =>
      activeNodes.map( server  => {
        val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + server)
        val snapshot = serverActor ? getSnapshotServer
        val result = Await.result(snapshot, timeout.duration)
        sender() ! result
        })
    }
}

object SupervisorActor {
  case class createServerActor()
  case class getDataSupervisor(id: Int)
  case class loadDataSupervisor(data: EntityDefinition)
  case class getSnapshot()
}


