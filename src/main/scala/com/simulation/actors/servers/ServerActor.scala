package com.simulation.actors.servers
import akka.actor.{Actor, ActorSelection}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor.{getDataServer, getFingerValue, getSnapshotServer, getSuccessor, initializeFingerTable, initializeFirstFingerTable, loadDataServer, updateOthers, updatePredecessor, updateTable}
import com.simulation.beans.EntityDefinition
import org.slf4j.{Logger, LoggerFactory}
import com.simulation.utils.ApplicationConstants

import scala.language.postfixOps
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ServerActor(id: Int, numNodes: Int) extends Actor {

  var dht = scala.collection.mutable.HashMap[Int, String]()
  var finger_table = scala.collection.mutable.LinkedHashMap[Int, Int]()
  var predecessor: Int = _
  var successor: Int = _
  val timeout = Timeout(200 seconds)
  val buckets = (Math.log(numNodes)/Math.log(2.0)).toInt
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  // Check if s belongs from n to fingerIthEntry
  def belongs(s:Int, n: Int, successorValue: Int): Boolean = {
    logger.info("Checking if "+s+ " belongs in the range "+n+" - "+successorValue)
    val nodeRanges:ListBuffer[Int] = if(n >= successorValue){
      //check if inclusive
      val temp = ListBuffer.range(n,numNodes)
      temp.addAll(ListBuffer.range(0,successorValue))
    } else{
        val temp = ListBuffer.range(successorValue,n)
        temp
      }
    if(nodeRanges.contains(s))
      return true
    false
  }

  override def receive = {

    case initializeFirstFingerTable(nodeIndex: Int) =>
      List.tabulate(buckets)(x => finger_table +=
        (((nodeIndex + math.pow(2, x)) % math.pow(2, buckets)).toInt -> nodeIndex))
      logger.info(finger_table.toString)
      predecessor = nodeIndex
      successor = nodeIndex

    case updatePredecessor(nodeIndex: Int) =>
      predecessor = nodeIndex

    case initializeFingerTable(nodeIndex: Int) =>
      val firstKey = ((nodeIndex + math.pow(2, 0)) % math.pow(2, buckets)).toInt
      val arbitraryNode = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + nodeIndex)
      logger.info(arbitraryNode.toString())
      logger.info(finger_table.toString)
      val successorValue = arbitraryNode ? findSuccessor(firstKey)
      val firstVal = Await.result(successorValue, 500.seconds).asInstanceOf[Int]
      logger.info("Successor Completed: " + finger_table.toString)
      finger_table += (firstKey -> firstVal)
      successor = firstVal
      val successorActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + finger_table(0))
      successorActor ! updatePredecessor(id)

      logger.info("First Instance: " + finger_table.toString)

      List.tabulate(buckets-1) ({ x =>
        val key = ((nodeIndex + math.pow(2, x + 1)) % math.pow(2, buckets)).toInt
        val successorValue = arbitraryNode ? findSuccessor(key)
        val Val = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
        finger_table += (key -> Val)
      })

      logger.info("Second Instance: " + finger_table.toString)


    case updateOthers(nodeIndex: Int) =>
      List.tabulate(buckets)(i => {
          val arbitraryActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + id)
          val arbitraryValue = arbitraryActor ? findPredecessor((nodeIndex - math.pow(2, i)).toInt)
          val predecessorValue = Await.result(arbitraryValue, timeout.duration).asInstanceOf[Int]
          val predecessorObject = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + predecessorValue)
          predecessorObject ! updateTable(predecessorValue, nodeIndex, i)
        })

      // predecessorValue -> n
      // nodeIndex -> s
      // i -> i
    case updateTable(predecessorValue:Int, nodeIndex: Int, i: Int) =>
      if(belongs(nodeIndex, predecessorValue, finger_table(i))){
        finger_table(i) = nodeIndex
        val predObj = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + predecessor)
        predObj ! updateTable(predecessor, nodeIndex, i)
      }

    case loadDataServer(data: EntityDefinition) =>
      logger.info("loadDataServer ServerActor")
      dht += (data.id -> data.name)
      val result = "Id: "+ data.id + ", Name: " + data.name
      sender() ! result

    case getDataServer(id: Int, m: Int, hash: Int) =>
      val output = getData(id: Int, m: Int, hash: Int)
      sender() ! output

    case getSuccessor() =>
      sender() ! successor

    case getSnapshotServer() =>
      logger.info("In getsnapshot server")
      sender() ! finger_table

    case getFingerValue() =>
      sender() ! finger_table(0)
  }

  def getData(id: Int, m: Int, hash: Int) : String = {
      if (m != buckets) {
        val fingerTBuffer = finger_table.toSeq
        List.tabulate(fingerTBuffer.size)(i =>
          if (belongs(hash, fingerTBuffer(i)._1, fingerTBuffer((i + 1) % fingerTBuffer.size)._1)) {
            val stockName = dht.get(id).head
            return (id + "->" + stockName)
          }
          else {
            val successorNode = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + fingerTBuffer(i)._2)
            successorNode ! getDataServer(id, m + 1, hash)
          })
      }
    ""
  }

  def findSuccessor(nodeIndex: Int) :Int = {
    logger.info("In find successor, node index = "+nodeIndex+" id = "+id)
    val arbitraryNode :Int = findPredecessor(nodeIndex)
    logger.info("In find successor, predecessor value = "+arbitraryNode)
    val successorActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + arbitraryNode)
    val successorValue = successorActor ? getSuccessor()
    val successorValueR = Await.result(successorValue, timeout.duration).toString.toInt
    logger.info("Successor Found, value = " + successorValueR)
    successorValueR
  }

  def findPredecessor(nodeIndex: Int): Int ={
    logger.info("In find predecessor, node index = "+nodeIndex+" id = "+id)
    var arbitraryNode = id
    logger.info("In find predecessor, successor node value = "+ successor)
    while(!belongs(nodeIndex, arbitraryNode , successor)){
      arbitraryNode = closest_preceding_finger(nodeIndex)
    }
    logger.info("Predecessor found, value = "+arbitraryNode)
    arbitraryNode
  }

  def closest_preceding_finger(nodeIndex: Int): Int = {
    val fingerTBuffer = finger_table.toSeq
    for (i <- (0 until buckets).reverse) {
      val temp = fingerTBuffer(i)._2
      logger.info(temp+"")
      if(belongs(temp, id, nodeIndex)){
        logger.info("Found closest preceding finger, value = " + temp)
        return temp
      }
    }
    logger.info("Found closest preceding finger, value = " + id)
    id
  }


}

object ServerActor {
  case class initializeFingerTable(nodeIndex: Int)
  case class initializeFirstFingerTable(nodeIndex: Int)
  case class updateFingerTable()
  case class getDataServer(nodeIndex: Int, m: Int, hash:Int)
  case class loadDataServer(data: EntityDefinition)
  case class findSuccessor(index: Int)
  case class updatePredecessor(nodeIndex: Int)
  case class updateOthers(nodeVal: Int)
  case class updateTable(predecessorValue: Int, nodeVal: Int, i: Int)
  case class getSnapshotServer()
  case class getFingerValue()
  case class getSuccessor()
}
