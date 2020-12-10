package com.simulation.actors.can

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import com.simulation.CANActorDriver.timeout
import com.simulation.actors.can.BootstrapActor._
import com.simulation.actors.can.NodeActor.{addNeighbour, fetchDHT, getNeighbours, loadDataNode, removeNeighbour, updateCoordinatesNode, updatePredecessor}
import com.simulation.beans.{Coordinates, EntityDefinition}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.concurrent.Await
import scala.util.Random

class BootstrapActor(system: ActorSystem) extends Actor {
  var activeNodes: mutable.Map[Int, Coordinates] = mutable.Map[Int, Coordinates]()
  var activeNodesActors: mutable.Map[Int, ActorRef] = mutable.Map[Int, ActorRef]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val conf = ConfigFactory.load("application.conf")

  def findNeighbours(server: Int, nodeActor:ActorRef): Unit = {
    val node: Coordinates = activeNodes(server)
    findXneighbours(node, nodeActor)
    findYneighbours(node, nodeActor)
  }


  def belongs(x1: Double, x2: Double, x3: Double, x4: Double, y1: Double, y2: Double, y3: Double, y4: Double): Boolean = {
    if (((x1 >= x3 && x1 <= x4) && (x2 >= x3 && x2 <= x4)) || ((x3 <= x1 && x3 <= x2) && (x4 >= x2 && x4 <= x2))) {
      if (y1 == y3 || y1 == y4 || y2 == y3 || y2 == y4)
        return true
    }
    false
  }

  def findXneighbours(node: Coordinates, nodeActor: ActorRef): Unit = {
    activeNodes.foreach{ case(index, neighbour)  => {
      if(neighbour!=node && belongs(node.x1, node.x2, neighbour.x1, neighbour.x2, node.y1, node.y2, neighbour.y1, neighbour.y2)){
        logger.info("Neighbour of server "+node+" -> "+neighbour)
        nodeActor ! addNeighbour(neighbour)
        activeNodesActors(neighbour.nodeIndex) ! addNeighbour(node)
      }
    }}
  }

  def findYneighbours(node: Coordinates, nodeActor: ActorRef): Unit = {
    activeNodes.foreach{ case(index, neighbour)  => {
      if(neighbour!=node && belongs(node.y1, node.y2, neighbour.y1, neighbour.y2, node.x1, node.x2, neighbour.x1, neighbour.x2)){
        logger.info("Neighbour of server "+node+" -> "+neighbour)
        nodeActor ! addNeighbour(neighbour)
        activeNodesActors(neighbour.nodeIndex) ! addNeighbour(node)
      }
    }}
  }

  def updateNeighbours(nodeIndex : Int): Unit ={
    val node = activeNodes(nodeIndex)
    val resultFuture = activeNodesActors(nodeIndex)  ? getNeighbours()
    val result = Await.result(resultFuture, timeout.duration).asInstanceOf[mutable.Map[Int, Coordinates]]

    result.foreach{ case(index, neighbour)  => {
      if(!belongs(node.y1, node.y2, neighbour.y1, neighbour.y2, node.x1, node.x2, neighbour.x1, neighbour.x2)
        && !belongs(node.x1, node.x2, neighbour.x1, neighbour.x2, node.y1, node.y2, neighbour.y1, neighbour.y2)){
        activeNodesActors(nodeIndex)  ! removeNeighbour(index)
        activeNodesActors(index)  ! removeNeighbour(nodeIndex)
      }
    }}
  }


  def searchNode(start:Int, id:Int): String ={
    val exploredSet = scala.collection.mutable.Set[Int]()
    val s = mutable.Stack[Int]()
    s.push(start)
    while (!s.isEmpty) {
      val node: Int = s.pop
      exploredSet += node
      val resultData = activeNodesActors(node)  ? fetchDHT(id)
      val result =  Await.result(resultData, timeout.duration).asInstanceOf[String]
      if(result!="") return result
      val resultNeighbours = activeNodesActors(node) ? getNeighbours()
      val resultNeighboursInfo =  Await.result(resultNeighbours, timeout.duration).asInstanceOf[mutable.Map[Int, Coordinates]]
      resultNeighboursInfo.keySet.foreach(neighbour => {
        s.push(neighbour)
      })
    }
    ""
  }

  def updateCoordinates(node: Coordinates) :Unit = {
    activeNodesActors.foreach{case(index, actor)  => {
      actor ! updateCoordinatesNode(node)
    }}
  }

  override def receive: Receive = {

    case createServerActorCAN(serverCounter: Int) => {
      val nodeActor = system.actorOf(Props(new NodeActor()), "node_actor_" + serverCounter)
      if(activeNodes.isEmpty){
        activeNodes += serverCounter -> Coordinates(serverCounter,0.0,1.0,0.0,1.0)
      }else {
        val nodeIndex = Random.nextInt(activeNodes.size)
        logger.info("Node being split => "+ nodeIndex)
        nodeActor ! updatePredecessor(nodeIndex)
        val node = activeNodes(nodeIndex)
        val xdiff = Math.abs(node.x2 - node.x1)/2
        val ydiff = Math.abs(node.y2 - node.y1)/2
        if(xdiff>ydiff){
          activeNodes += nodeIndex -> Coordinates(nodeIndex, node.x1, node.x2 - xdiff, node.y1, node.y2)
          activeNodes += serverCounter -> Coordinates(serverCounter, node.x1 + xdiff, node.x2, node.y1, node.y2)
        } else {
          activeNodes += nodeIndex -> Coordinates(nodeIndex, node.x1, node.x2, node.y1, node.y2 - ydiff)
          activeNodes += serverCounter -> Coordinates(serverCounter, node.x1, node.x2, node.y1 + ydiff, node.y2)
        }
        findNeighbours(serverCounter, nodeActor)
        updateNeighbours(nodeIndex)
        updateCoordinates(activeNodes(nodeIndex))
      }
      logger.info("Active nodes" + activeNodes)
      activeNodesActors += serverCounter -> nodeActor
    }
    case getCanNodes() => {
      sender() ! activeNodes
    }
    case getDataBootstrapCAN(id: Int) => {
      val startNode = Random.nextInt(activeNodes.size)
      sender() ! searchNode(startNode, id)
    }
    case loadDataBootstrapCAN(data: EntityDefinition) => {
      val nodeIndex = Random.nextInt(activeNodes.size)
      logger.info("Node where to load data => "+ nodeIndex)
      activeNodesActors(nodeIndex) ! loadDataNode(data)
      sender() ! nodeIndex
    }
    case getSnapshotCAN() => {
      var outputString = ""
      activeNodesActors.foreach{case(node,actor) => {
        val resultFuture = actor ? getNeighbours()
        val result = Await.result(resultFuture, timeout.duration)
        logger.info(result.toString)
        outputString += node + " "+ activeNodes(node) +" -> " +result.toString + "<br>"
      }}
      sender() ! outputString
    }

    case removeBootstrapNode(nodeIndex:Int) => {
      val resultFuture = activeNodesActors(nodeIndex) ? getNeighbours()
      val result = Await.result(resultFuture, timeout.duration).asInstanceOf[mutable.Map[Int, Coordinates]]
      activeNodesActors.remove(nodeIndex)
      activeNodes.remove(nodeIndex)
      logger.info("Node removed")
      result.foreach{case(index, neighbour)  => {
        activeNodesActors(neighbour.nodeIndex) ! removeNeighbour(nodeIndex)
        findNeighbours(index, activeNodesActors(neighbour.nodeIndex))
      }}

    }
  }
}

object BootstrapActor {
  case class createServerActorCAN(serverCount: Int)
  case class getDataBootstrapCAN(id: Int)
  case class loadDataBootstrapCAN(data: EntityDefinition)
  case class getSnapshotCAN()
  case class getCanNodes()
  case class removeBootstrapNode(nodeIndex:Int)

}