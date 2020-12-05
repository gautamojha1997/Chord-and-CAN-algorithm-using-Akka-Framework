package com.simulation.actors.can

import akka.actor.Actor
import com.simulation.actors.can.BootstrapActor._
import com.simulation.beans.{Coordinates, EntityDefinition}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

class BootstrapActor extends Actor {
  var activeNodes = ListBuffer[Coordinates]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val conf = ConfigFactory.load("application.conf")

  override def receive: Receive = {

    case createServerActorCAN() => {
      if(activeNodes.size==0){
        activeNodes += Coordinates(0.0,1.0,0.0,1.0)
      }else {
        val nodeIndex = Random.nextInt(activeNodes.size)
        logger.info("Node being split =>",nodeIndex)
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
    }
    case getCanNodes() => {
      sender() ! activeNodes
    }
    case getDataBootstrapCAN(id: Int) => {

    }
    case loadDataBootstrapCAN(data: EntityDefinition) => {

    }
    case getSnapshotCAN() => {

    }

  }
}

object BootstrapActor {
  case class createServerActorCAN()
  case class getDataBootstrapCAN(id: Int)
  case class loadDataBootstrapCAN(data: EntityDefinition)
  case class getSnapshotCAN()
  case class getCanNodes()
}