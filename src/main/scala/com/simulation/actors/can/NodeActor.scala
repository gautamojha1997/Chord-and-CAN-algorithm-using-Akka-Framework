package com.simulation.actors.can

import akka.actor.Actor
import com.simulation.actors.can.NodeActor.{addNeighbour, fetchDHT, getNeighbours, loadDataNode, removeNeighbour, updatePredecessor}
import com.simulation.beans.{Coordinates, EntityDefinition}

import scala.collection.mutable

class NodeActor extends Actor{

  var dht: mutable.Map[Int, String] = mutable.HashMap[Int, String]()
  val neighbours: mutable.Map[Int, Coordinates] = mutable.HashMap[Int, Coordinates]()
  var predecessor :Int = -1

  override def receive: Receive = {
    case fetchDHT(id: Int) =>
      if (dht.keySet.contains(id)) {
        sender() ! dht(id)
      }
      sender() ! ""

    case loadDataNode(data: EntityDefinition) => {
      dht(data.id) = data.name
    }

    case addNeighbour(coordinates: Coordinates) => {
      neighbours(coordinates.nodeIndex) = coordinates
    }

    case updatePredecessor(server: Int) =>{
      predecessor = server
    }

    case getNeighbours() => {
      sender() ! neighbours
    }

    case removeNeighbour(server: Int) =>{
      neighbours.remove(server)
    }
  }
}

object NodeActor{
  case class fetchDHT(id: Int)
  case class loadDataNode(data: EntityDefinition)
  case class addNeighbour(coordinates: Coordinates)
  case class updatePredecessor(server: Int)
  case class getNeighbours()
  case class removeNeighbour(server: Int)
}
