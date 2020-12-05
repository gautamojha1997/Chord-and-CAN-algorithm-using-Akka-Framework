package com.simulation.actors.can

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.simulation.actors.can.BootstrapActor._
import com.simulation.actors.can.NodeActor.loadDataNode
import com.simulation.beans.{Coordinates, EntityDefinition}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.util.Random

class BootstrapActor(system: ActorSystem) extends Actor {
  var activeNodes = ListBuffer[Coordinates]()
  var activeNodesActors = ListBuffer[ActorRef]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val conf = ConfigFactory.load("application.conf")

  override def receive: Receive = {

    case createServerActorCAN(serverCount: Int) => {
      val nodeActor :ActorRef = system.actorOf(Props(new NodeActor()), "node_actor_" + serverCount)
      if(activeNodes.size==0){
        activeNodes += Coordinates(0.0,1.0,0.0,1.0)
      }else {
        val nodeIndex = Random.nextInt(activeNodes.size)
        logger.info("Node being split => "+ nodeIndex)
        val node: Coordinates = activeNodes(nodeIndex)
        val xdiff = Math.abs(node.x2 - node.x1)/2
        val ydiff = Math.abs(node.y2 - node.y1)/2
        if(xdiff>ydiff){
          activeNodes(nodeIndex) = Coordinates(node.x1, node.x2 - xdiff, node.y1, node.y2)
          activeNodes += Coordinates(node.x1 + xdiff, node.x2, node.y1, node.y2)
        } else {
          activeNodes(nodeIndex) = Coordinates(node.x1, node.x2, node.y1, node.y2 - ydiff)
          activeNodes += Coordinates(node.x1, node.x2, node.y1 + ydiff, node.y2)
        }
      }
      activeNodesActors += nodeActor
    }
    case getCanNodes() => {
      sender() ! activeNodes
    }
    case getDataBootstrapCAN(id: Int) => {


    }
    case loadDataBootstrapCAN(data: EntityDefinition) => {
      val nodeIndex = Random.nextInt(activeNodes.size)
      logger.info("Node where to load data => "+ nodeIndex)
      activeNodesActors(nodeIndex) ! loadDataNode(data)
      sender() ! nodeIndex
    }
    case getSnapshotCAN() => {

    }

  }
}

object BootstrapActor {
  case class createServerActorCAN(serverCount: Int)
  case class getDataBootstrapCAN(id: Int)
  case class loadDataBootstrapCAN(data: EntityDefinition)
  case class getSnapshotCAN()
  case class getCanNodes()
}