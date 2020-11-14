package com.simulation.actors.supervisors

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor
import com.simulation.actors.servers.ServerActor.{getDataServer, getSnapshotServer, initializeFingerTable, initializeFirstFingerTable, loadDataServer, updateOthers}
import com.simulation.actors.supervisors.SupervisorActor.{createServerActor, getDataSupervisor, getSnapshot, loadDataSupervisor}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.{ApplicationConstants, Data}
import com.simulation.utils.Utility.md5
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random
import scala.language.postfixOps


class SupervisorActor(id: Int, numNodes: Int) extends Actor{

  var nodesActorMapper: mutable.Map[Int, Int] = mutable.HashMap[Int, Int]()
  val system: ActorSystem = ActorSystem()
  val timeout = Timeout(30 seconds)
  //check if inclusive
  val unexploredNodes = ListBuffer.range(0,numNodes)
  var activeNodes: mutable.TreeSet[Int] = new mutable.TreeSet[Int]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def receive: Receive = {

    case createServerActor() => {
      val nodeIndex = unexploredNodes(Random.nextInt(unexploredNodes.size))
      val serverActor = system.actorOf(Props(new ServerActor(nodeIndex, numNodes)), "server_actor_" + nodeIndex)
      if(activeNodes.nonEmpty){

//        val successorNode = activeNodes.minAfter(nodeIndex+1)
//        val successorValue = if (!successorNode.isEmpty) successorNode.head else activeNodes.toList(0)
        serverActor ! initializeFingerTable(nodeIndex)
        serverActor ! updateOthers(nodeIndex)
      }
      else
        serverActor ! initializeFirstFingerTable(nodeIndex)

      unexploredNodes -= nodeIndex
      activeNodes.add(nodeIndex)
<<<<<<< HEAD
      logger.info(activeNodes.toString)

=======
>>>>>>> origin/master
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
      var chosenNode = activeNodes.minAfter(hash).toString
      if(chosenNode == "None")
        chosenNode = activeNodes.head.toString
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + chosenNode)
      serverActor ! loadDataServer(data)
    }

    case getSnapshot() =>
      activeNodes.map( server  => {
        logger.info("Get Snapshot")
        val serverActor = context.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + server)
        logger.info(serverActor.toString())
        val snapshot = serverActor ? getSnapshotServer()
        val result = Await.result(snapshot, timeout.duration)
        sender() ! result
        })
    }
}

object SupervisorActor {
  case class createServerActor()
  case class getDataSupervisor(id: Int)
  case class loadDataSupervisor(data: Data)
  case class getSnapshot()
}


