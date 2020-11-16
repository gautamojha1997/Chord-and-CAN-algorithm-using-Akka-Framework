package com.simulation.actors.servers
import akka.actor.{Actor, ActorSelection, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor.{findSuccessor, getDataServer, getSnapshotServer, initializeFingerTable, initializeFirstFingerTable, loadDataServer, updateOthers, updateTable}
import com.simulation.beans.EntityDefinition
import org.slf4j.{Logger, LoggerFactory}
import com.simulation.utils.ApplicationConstants
import com.simulation.utils.FingerActor.{fetchFingerTable, getFingerValue, getPredecessor, getSuccessor, setFingerValue, setPredecessor, setSuccessor, updateFingerTable}

import scala.collection.mutable
import scala.language.postfixOps
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ServerActor(id: Int, numNodes: Int) extends Actor {

  val fingerNode = context.system.actorSelection("akka://actorSystem/user/finger_actor")
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
      fingerNode ! setPredecessor(nodeIndex, nodeIndex)
      fingerNode ! setSuccessor(nodeIndex, nodeIndex)
      fingerNode ! updateFingerTable(finger_table, nodeIndex)

    case initializeFingerTable(nodeIndex: Int) =>
      val firstKey = ((id + math.pow(2, 0)) % math.pow(2, buckets)).toInt
      val arbitraryNode = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + nodeIndex)
      logger.info(arbitraryNode.toString())
      logger.info(finger_table.toString)
      val successorValue = arbitraryNode ? findSuccessor(firstKey)
      val firstVal = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
      logger.info("Successor Completed: " + finger_table.toString)
      finger_table += (firstKey -> firstVal)
      fingerNode ! setSuccessor(id, firstVal)
      val newPredecessor = fingerNode ? getPredecessor(firstVal)
      val newPredecessorR = Await.result(newPredecessor, timeout.duration).asInstanceOf[Int]
      fingerNode ! setPredecessor(id, newPredecessorR)
      fingerNode ! setPredecessor(newPredecessorR, id)

      logger.info("First Instance: " + finger_table.toString)

      (0 to buckets-2).foreach({ i =>
        val key = ((id + math.pow(2, i + 1)) % math.pow(2, buckets)).toInt
        val initialValue = finger_table.toSeq(i)._2
        if(belongs(key, id, initialValue)) {
          val successorValueR = initialValue
          finger_table += (key -> successorValueR)
        }
        else{
          val successorValue = arbitraryNode ? findSuccessor(key)
          val successorValueR = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
          finger_table += (key -> successorValueR)
        }
      })
      logger.info("Second Instance: " + finger_table.toString)
      val ftable = fingerNode ? fetchFingerTable(nodeIndex)
      val ftableN  = Await.result(ftable, timeout.duration).asInstanceOf[mutable.LinkedHashMap[Int,Int]]
      logger.info("Second Instance for Node Index: " + ftableN.toString)
      fingerNode ! updateFingerTable(finger_table, id)

    case updateOthers() =>
      for (i <- 1 until buckets) {
        val predecessorValue = findPredecessor(((buckets + id - math.pow(2, i - 1)) % math.pow(2,buckets)).toInt)
        val predecessorObject = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + predecessorValue)
        predecessorObject ! updateTable(id, i)
      }
      val fTable = fingerNode ? fetchFingerTable(id).asInstanceOf[mutable.LinkedHashMap[Int, Int]]
      logger.info(fTable.toString)


      // predecessorValue -> n
      // nodeIndex -> s
      // i -> i
    case updateTable(s: Int, i: Int) =>
      val fingerValue = fingerNode ? getFingerValue(id, i)
      val fingerValueR = Await.result(fingerValue, timeout.duration).asInstanceOf[Int]
      if(belongs(s, id, fingerValueR)){
        fingerNode ! setFingerValue(id, i, s)
        val predecessorValue = fingerNode ? getPredecessor(id)
        val predecessorValueR = Await.result(predecessorValue, timeout.duration).asInstanceOf[Int]
        val predObj = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + predecessorValueR)
        predObj ! updateTable(s, i)
      }

    case loadDataServer(data: EntityDefinition) =>
      logger.info("loadDataServer ServerActor")
      dht += (data.id -> data.name)
      val result = "Id: "+ data.id + ", Name: " + data.name
      sender() ! result

    case getDataServer(id: Int, m: Int, hash: Int) =>
      val output = getData(id: Int, m: Int, hash: Int)
      sender() ! output

    case getSnapshotServer() =>
      logger.info("In getsnapshot server")
      sender() ! finger_table

    case findSuccessor(nodeIndex: Int) =>
      logger.info("In find successor, node index = " + nodeIndex + " id = " + id)
      val arbitraryNode: Int = findPredecessor(nodeIndex)
      logger.info("In find successor, predecessor value = " + arbitraryNode)
      val successorValue = fingerNode ? getSuccessor(arbitraryNode)
      val successorValueR = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
      logger.info("Successor Found, value = " + successorValueR)
      sender() ! successorValueR
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

  def findPredecessor(nodeIndex: Int): Int ={
    logger.info("In find predecessor, node index = "+nodeIndex+" id = "+id)
    var arbitraryNode = id
    val successorValue = fingerNode ? getSuccessor(arbitraryNode)
    val successorValueR = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
    logger.info("In find predecessor, successor node value = "+ successorValueR)
    while(!belongs(nodeIndex, arbitraryNode , successorValueR)){
      arbitraryNode =  closestPrecedingFinger(nodeIndex)
    }
    logger.info("Predecessor found, value = "+arbitraryNode)
    arbitraryNode
  }

  def closestPrecedingFinger(nodeIndex: Int): Int = {
    var closestIndex = id
    for (i <- (0 until buckets).reverse) {
      val fingerValue = fingerNode ? getFingerValue(id, i)
      val fingerValueR = Await.result(fingerValue, timeout.duration).asInstanceOf[Int]
      logger.info("In closest preceeding finger, value = "+fingerValueR+"")
      if(belongs(fingerValueR, id, nodeIndex)){
        logger.info("Found closest preceding finger, value = " + fingerValueR)
        closestIndex = fingerValueR
      }
    }
    logger.info("Found closest preceding finger, value = " + id)
    closestIndex
  }


}

object ServerActor {
  case class initializeFingerTable(nodeIndex: Int)
  case class initializeFirstFingerTable(nodeIndex: Int)
  case class getDataServer(nodeIndex: Int, m: Int, hash:Int)
  case class loadDataServer(data: EntityDefinition)
  case class findSuccessor(index: Int)
  case class updateOthers()
  case class updateTable(s: Int, i: Int)
  case class getSnapshotServer()
}
