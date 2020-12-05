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
      if(activeNodes==0){
        activeNodes += Coordinates(0.0,1.0,0.0,1.0)
      }
      val node :Coordinates = activeNodes(Random.nextInt(activeNodes.size))
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