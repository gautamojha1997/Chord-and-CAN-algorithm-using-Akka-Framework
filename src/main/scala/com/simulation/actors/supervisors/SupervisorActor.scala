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
import scala.tools.nsc.doc.model.Entity

class SupervisorActor(id: Int, numNodes: Int, system: ActorSystem) extends Actor{

  var nodesActorMapper: mutable.Map[Int, Int] = mutable.HashMap[Int, Int]()
  //val system: ActorSystem = ActorSystem("actorSystem")
  val timeout = Timeout(30 seconds)
  //check if inclusive
  val unexploredNodes = ListBuffer.range(0,numNodes)
  var activeNodes: mutable.TreeSet[Int] = new mutable.TreeSet[Int]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def receive: Receive = {

    case createServerActor() => {
      val nodeIndex = unexploredNodes(Random.nextInt(unexploredNodes.size))
      logger.info("Sever Actor Created: " + nodeIndex)
      if(activeNodes.nonEmpty){
        val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + activeNodes.toList(0))
        //        val successorNode = activeNodes.minAfter(nodeIndex+1)
//        val successorValue: Int = if (!successorNode.isEmpty) successorNode.head else activeNodes.toList(0)
        serverActor ! initializeFingerTable(nodeIndex)
        serverActor ! updateOthers(nodeIndex)
      }
      else {
        val serverActor = system.actorOf(Props(new ServerActor(nodeIndex, numNodes)), "server_actor_" + nodeIndex)
        serverActor ! initializeFirstFingerTable(nodeIndex)
      }
      unexploredNodes -= nodeIndex
      activeNodes.add(nodeIndex)
      logger.info(activeNodes.toString)

    }

    case getDataSupervisor(id) => {
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + activeNodes.toList(Random.nextInt(activeNodes.size)))
      val data = serverActor ? getDataServer(id,0)
      val result = Await.result(data, timeout.duration)
      sender() ! result
    }

    // implement hashing function & load the data in appropriate node
    case loadDataSupervisor(data) => {
      logger.info("In loadDataSupervisor SupevisorActor")
      val hash = md5(data.id.toString, numNodes)
      var chosenNode = activeNodes.minAfter(hash).toString
      if(chosenNode == "None")
        chosenNode = activeNodes.head.toString
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + chosenNode)
      val resultFuture = serverActor ? loadDataServer(data)
      val result = Await.result(resultFuture, timeout.duration)
      sender() ! result
      //serverActor ! loadDataServer(data)

    }

    case getSnapshot() =>
      var outputString = ""
      activeNodes.map( server  => {
        logger.info("Get Snapshot")
        val serverActor = context.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + server)
        logger.info(serverActor.pathString)
        val snapshot = serverActor ? getSnapshotServer()
        val result = Await.result(snapshot, timeout.duration)
        logger.info(result.toString)
        outputString += server +" -> " + result.toString + "\n"
        })
      sender() ! outputString
    }
}

object SupervisorActor {
  case class createServerActor()
  case class getDataSupervisor(id: Int)
  case class loadDataSupervisor(data: EntityDefinition)
  case class getSnapshot()
}


