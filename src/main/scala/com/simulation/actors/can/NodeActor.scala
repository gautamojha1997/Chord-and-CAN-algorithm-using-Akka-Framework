package com.simulation.actors.can

import akka.actor.Actor
import com.simulation.actors.can.NodeActor.{fetchDHT, loadDataNode}
import com.simulation.beans.{Coordinates, EntityDefinition}

import scala.collection.mutable

class NodeActor extends Actor{

  var dht = mutable.HashMap[Int, String]()
  val neighbours = mutable.HashMap[Int, Coordinates]()

  override def receive: Receive = {
    case fetchDHT(id: Int) => {
      val output = dht(id)
      sender() ! output
    }

    case loadDataNode(data: EntityDefinition) => {
      dht(data.id) = data.name
    }
  }
}

object NodeActor{
  case class fetchDHT(id: Int)
  case class loadDataNode(data: EntityDefinition)
}
