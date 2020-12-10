package com.simulation.actors.can

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import com.simulation.CANActorDriver.timeout
import com.simulation.ConnectToCassandra
import com.simulation.actors.can.BootstrapActor._
import com.simulation.actors.can.NodeActor.{addNeighbour, fetchDHT, getNeighbours, loadDataNode, removeNeighbour, transferDHT, updateCoordinatesNode}
import com.simulation.beans.{Coordinates, EntityDefinition}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.concurrent.Await
import scala.util.Random

/**
 * Class which handles the bootstrap server actor which keeps track of all CAN nodes
 * @param system Actor System
 */
class BootstrapActor(system: ActorSystem) extends Actor {
  var activeNodes: mutable.Map[Int, Coordinates] = mutable.Map[Int, Coordinates]()
  var activeNodesActors: mutable.Map[Int, ActorRef] = mutable.Map[Int, ActorRef]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val conf: Config = ConfigFactory.load("application.conf")

  /**
   * Fetches all the neighbours of current node
   * @param server Current node counter
   * @param nodeActor Reference of current node
   */
  def findNeighbours(server: Int, nodeActor:ActorRef): Unit = {
    val node: Coordinates = activeNodes(server)
    findXneighbours(node, nodeActor)
    findYneighbours(node, nodeActor)
  }

  /**
   * Method checks if 2 nodes are neighbours using cartesian coordinates
   * @param x1 coordinate of node
   * @param x2 coordinate of node
   * @param x3 coordinate of neighbour
   * @param x4 coordinate of neighbour
   * @param y1 coordinate of node
   * @param y2 coordinate of node
   * @param y3 coordinate of neighbour
   * @param y4 coordinate of neighbour
   * @return
   */
  def belongs(x1: Double, x2: Double, x3: Double, x4: Double, y1: Double, y2: Double, y3: Double, y4: Double): Boolean = {
    if (((x1 >= x3 && x1 <= x4) && (x2 >= x3 && x2 <= x4)) || ((x3 <= x1 && x3 <= x2) && (x4 >= x2 && x4 <= x2))) {
      if (y1 == y3 || y1 == y4 || y2 == y3 || y2 == y4)
        return true
    }
    false
  }

  /**
   * Finds all the X axis neighbours
   * @param node Current node to be added
   * @param nodeActor Reference of current node
   */
  def findXneighbours(node: Coordinates, nodeActor: ActorRef): Unit = {
    activeNodes.foreach{ case(_, neighbour)  =>
      if(neighbour!=node && belongs(node.x1, node.x2, neighbour.x1, neighbour.x2, node.y1, node.y2, neighbour.y1, neighbour.y2)){
        logger.info("Neighbour of server "+node+" -> "+neighbour)
        nodeActor ! addNeighbour(neighbour)
        activeNodesActors(neighbour.nodeIndex) ! addNeighbour(node)
      }
    }
  }

  /**
   * Finds all the Y axis neighbours
   * @param node Current node to be added
   * @param nodeActor Reference of current node
   */
  def findYneighbours(node: Coordinates, nodeActor: ActorRef): Unit = {
    activeNodes.foreach{ case(_, neighbour)  =>
      if(neighbour!=node && belongs(node.y1, node.y2, neighbour.y1, neighbour.y2, node.x1, node.x2, neighbour.x1, neighbour.x2)){
        logger.info("Neighbour of server "+node+" -> "+neighbour)
        nodeActor ! addNeighbour(neighbour)
        activeNodesActors(neighbour.nodeIndex) ! addNeighbour(node)
      }
    }
  }

  /**
   * Updates neighbours when node a node is split to check if neighbours have changed
   * @param nodeIndex  Node which has been split
   */
  def updateNeighbours(nodeIndex : Int): Unit ={
    val node = activeNodes(nodeIndex)
    val resultFuture = activeNodesActors(nodeIndex)  ? getNeighbours()
    val result = Await.result(resultFuture, timeout.duration).asInstanceOf[mutable.Map[Int, Coordinates]]

    result.foreach{ case(index, neighbour)  =>
      if(!belongs(node.y1, node.y2, neighbour.y1, neighbour.y2, node.x1, node.x2, neighbour.x1, neighbour.x2)
        && !belongs(node.x1, node.x2, neighbour.x1, neighbour.x2, node.y1, node.y2, neighbour.y1, neighbour.y2)){
        activeNodesActors(nodeIndex)  ! removeNeighbour(index)
        activeNodesActors(index)  ! removeNeighbour(nodeIndex)
      }
    }
  }

  /**
   * Hops thru neighbours using dfs to locate the node where id exists
   * @param start Initial node to begin the search
   * @param id The data id to be located
   * @return Value of that data id
   */
  def searchNode(start:Int, id:Int): String ={
    logger.info("Searching id = "+id+" -> Starting from node ="+start)
    val exploredSet = scala.collection.mutable.Set[Int]()
    val s = mutable.Stack[Int]()
    s.push(start)
    while (s.nonEmpty) {
      val node: Int = s.pop
      exploredSet += node
      val resultData = activeNodesActors(node) ? fetchDHT()
      val resultMap =  Await.result(resultData, timeout.duration).asInstanceOf[mutable.HashMap[Int, String]]
      if(resultMap.contains(id)){
        logger.info("Found in node = "+node)
        return resultMap(id)
      }
      val resultNeighbours = activeNodesActors(node) ? getNeighbours()
      val resultNeighboursInfo =  Await.result(resultNeighbours, timeout.duration).asInstanceOf[mutable.Map[Int, Coordinates]]
      resultNeighboursInfo.keySet.foreach(neighbour => {
        if(!exploredSet.contains(neighbour)) {
          s.push(neighbour)
        }
      })
    }
    ""
  }

  /**
   * Update node coordinate changes to consequent nodes
   * @param node Current node whose coordinates have changed
   */

  def updateCoordinates(node: Coordinates) :Unit = {
    logger.info("Updating coordinates of node => "+node)
    val resultNeighbourNodes = activeNodesActors(node.nodeIndex) ? getNeighbours()
    val resultNeighbours = Await.result(resultNeighbourNodes, timeout.duration).asInstanceOf[mutable.Map[Int, Coordinates]]
    resultNeighbours.foreach{case(index, _)  =>
      activeNodesActors(index) ! updateCoordinatesNode(node)
    }
  }

  override def receive: Receive = {

    case createServerActorCAN(serverCounter: Int) =>
      logger.info("Node being added => "+ serverCounter)
      val nodeActor = system.actorOf(Props(new NodeActor()), "node_actor_" + serverCounter)
      activeNodesActors += serverCounter -> nodeActor
      if(activeNodes.isEmpty){
        activeNodes += serverCounter -> Coordinates(serverCounter,0.0,1.0,0.0,1.0)
      }else {
        val nodeIndex = activeNodes.keySet.toSeq(Random.nextInt(activeNodes.keySet.size))
        logger.info("Node being split => "+ nodeIndex)
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

    case getDataBootstrapCAN(id: Int) =>
      logger.info("Get row => "+ id)
      val startNode = activeNodes.keySet.toSeq(Random.nextInt(activeNodes.keySet.size))
      sender() ! searchNode(startNode, id)

    case loadDataBootstrapCAN(data: EntityDefinition) =>
      val nodeIndex = activeNodes.keySet.toSeq(Random.nextInt(activeNodes.keySet.size))
      logger.info("Node where to load data => "+ nodeIndex)
      activeNodesActors(nodeIndex) ! loadDataNode(data)
      sender() ! nodeIndex

      //change the confValue to true in application.conf to have data persistence
      //make sure to install cassandra first

      if(conf.getBoolean("enableCassandra")) {
        activeNodesActors(nodeIndex) ! ConnectToCassandra.createTable()
        activeNodesActors(nodeIndex) ! ConnectToCassandra.addToCassandra(data)
      }

    case getSnapshotCAN() =>
      var outputString = ""
      activeNodesActors.foreach{case(node,actor) =>
        val resultFuture = actor ? getNeighbours()
        val result = Await.result(resultFuture, timeout.duration)
        logger.info(result.toString)
        outputString += node + " "+ activeNodes(node) +" -> " +result.toString + "<br>"
      }
      sender() ! outputString

    case removeBootstrapNode(nodeIndex:Int) =>
      val resultNeighbourNodes = activeNodesActors(nodeIndex) ? getNeighbours()
      val resultNeighbours = Await.result(resultNeighbourNodes, timeout.duration).asInstanceOf[mutable.Map[Int, Coordinates]]
      val resultData = activeNodesActors(nodeIndex) ? fetchDHT()
      val resultKeys = Await.result(resultData, timeout.duration).asInstanceOf[mutable.HashMap[Int, String]]
      if(resultNeighbours.nonEmpty){
        activeNodesActors(resultNeighbours.toSeq.head._1) ! transferDHT(resultKeys)
        logger.info("Keys moved to "+resultNeighbours.toSeq.head._1)
      }
      activeNodesActors.remove(nodeIndex)
      activeNodes.remove(nodeIndex)
      logger.info("Node removed")
      resultNeighbours.foreach{case(index, neighbour)  =>
        activeNodesActors(neighbour.nodeIndex) ! removeNeighbour(nodeIndex)
        findNeighbours(index, activeNodesActors(neighbour.nodeIndex))
      }
  }
}

object BootstrapActor {
  case class createServerActorCAN(serverCount: Int)
  case class getDataBootstrapCAN(id: Int)
  case class loadDataBootstrapCAN(data: EntityDefinition)
  case class getSnapshotCAN()
  case class removeBootstrapNode(nodeIndex:Int)

}