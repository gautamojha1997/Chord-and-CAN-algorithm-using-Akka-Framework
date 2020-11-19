package com.simulation.actors.supervisors

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.ConnectToCassandra
import com.simulation.actors.servers.ServerActor
import com.simulation.actors.servers.ServerActor.{getDataServer, getSnapshotServer, initializeFingerTable, initializeFirstFingerTable, loadDataServer, updateOthers}
import com.simulation.actors.supervisors.SupervisorActor.{createServerActor, getDataSupervisor, getSnapshot, loadDataSupervisor}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.FingerActor.fetchFingerTable
import com.simulation.utils.ApplicationConstants
import com.simulation.utils.Utility.md5
import com.typesafe.config.ConfigFactory
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
  val timeout = Timeout(50 seconds)
  val unexploredNodes = ListBuffer.range(0,numNodes)
  var activeNodes: mutable.TreeSet[Int] = new mutable.TreeSet[Int]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val fingerNode = context.system.actorSelection("akka://actorSystem/user/finger_actor")
  val conf = ConfigFactory.load("application.conf")

  override def receive: Receive = {

    case createServerActor() => {
      val nodeIndex = unexploredNodes(Random.nextInt(unexploredNodes.size))
      val serverActor = system.actorOf(Props(new ServerActor(nodeIndex, numNodes)), "server_actor_" + nodeIndex)
      logger.info("Sever Actor Created: " + nodeIndex)
      if(activeNodes.nonEmpty){
        serverActor ! initializeFingerTable(activeNodes.head)
        serverActor ! updateOthers(activeNodes)
      }
      else {
        serverActor ! initializeFirstFingerTable(nodeIndex)
      }
      activeNodes.add(nodeIndex)
      unexploredNodes -= nodeIndex

    }

    case getDataSupervisor(id) => {
      val hash = md5(id.toString, numNodes) % numNodes
      val chosenNode = activeNodes.minAfter(hash)
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + activeNodes.head) // (Random.nextInt(activeNodes.size)))
      val data = serverActor ? getDataServer(id,hash)
      val result = Await.result(data, timeout.duration)
      sender() ! result
    }

    // implement hashing function & load the data in appropriate node
    case loadDataSupervisor(data) => {
      logger.info("In loadDataSupervisor SupevisorActor")
      val hash = md5(data.id.toString, numNodes) % numNodes
      val serverActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + activeNodes.head)
      val resultFuture = serverActor ? loadDataServer(data, activeNodes.head, hash)
      val result = Await.result(resultFuture, timeout.duration)

      sender() ! result
      //change the confValue to true in application.conf to have data persistence
      //make sure to install cassandra first

      if(conf.getBoolean("enableCassandra")) {
        serverActor ! ConnectToCassandra.createTable()
        serverActor ! ConnectToCassandra.addToCassandra(data)
      }
    }

    case getSnapshot() =>
      var outputString = ""
      activeNodes.map( server  => {
        logger.info("Get Snapshot")
        val snapshot = fingerNode ? fetchFingerTable(server)
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


