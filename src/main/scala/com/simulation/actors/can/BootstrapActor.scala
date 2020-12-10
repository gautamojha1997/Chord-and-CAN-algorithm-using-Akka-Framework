package com.simulation.actors.can

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.testkit.TestActor.NullMessage.sender
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

class BootstrapActor(system: ActorSystem) extends Actor {
  var activeNodes: mutable.Map[Int, Coordinates] = mutable.Map[Int, Coordinates]()
  var activeNodesActors: mutable.Map[Int, ActorRef] = mutable.Map[Int, ActorRef]()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val conf: Config = ConfigFactory.load("application.conf")

  def findNeighbours(server: Int, nodeActor:ActorRef): Unit = {
    val node: Coordinates = activeNodes(server)
    findXneighbours(node, nodeActor)
    findYneighbours(node, nodeActor)
  }

  /**
   * Method checks if 2 nodes are neighbours using cartesian coordinates
   */
  def belongs(x1: Double, x2: Double, x3: Double, x4: Double, y1: Double, y2: Double, y3: Double, y4: Double): Boolean = {
    if (((x1 >= x3 && x1 <= x4) && (x2 >= x3 && x2 <= x4)) || ((x3 <= x1 && x3 <= x2) && (x4 >= x2 && x4 <= x2))) {
      if (y1 == y3 || y1 == y4 || y2 == y3 || y2 == y4)
        return true
    }
    false
  }

  /**
   * Checks if X axis neighbour
   * @param node
   * @param nodeActor
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
   * Checks if Y axis neighbour
   * @param node
   * @param nodeActor
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
   * Updates neighbours when node is changed
   * @param nodeIndex
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
   * Hops thur neighbours to locate id
   * @param start
   * @param id
   * @return
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
   * @param node
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