package com.simulation.actors.servers
import akka.actor.{Actor, ActorSelection}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor.{getDataServer, getSnapshotServer, initializeFingerTable, initializeFirstFingerTable, loadDataServer, updateOthers, updatePredecessor, updateTable}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.ApplicationConstants
import com.simulation.utils.Utility

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ServerActor(id: Int, numNodes: Int) extends Actor {

  var dht = scala.collection.mutable.HashMap[Int, String]()
  var finger_table = scala.collection.mutable.LinkedHashMap[Int, Int]()
  var predecessor: Int = _
  val timeout = Timeout(10 seconds)
  val buckets = (Math.log(numNodes)/Math.log(2.0)).toInt
  var node: Int = _
  val m = (Math.log(numNodes)/Math.log(2)).toInt

  // Check if s belongs from n to fingerIthEntry
  def belongs(s:Int, n: Int, successorValue: Int): Boolean = {
    val nodeRanges:ListBuffer[Int] = if(n > successorValue){
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
      predecessor = nodeIndex

    case updatePredecessor(nodeIndex: Int) =>
      predecessor = nodeIndex

    case initializeFingerTable(nodeIndex: Int) =>
      node = nodeIndex
      val firstKey = ((nodeIndex + math.pow(2, 0)) % math.pow(2, buckets)).toInt
      val arbitraryNode = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + nodeIndex)
      val successorValue = arbitraryNode ? findSuccessor(firstKey)
      val firstVal = Await.result(successorValue, timeout.duration).toString.toInt
      finger_table += (firstKey -> firstVal)
      val successor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + finger_table(0))
      // check for successor or cuurent node
//      val futurePredecessor = findPredecessor(nodeIndex)
      successor ! updatePredecessor(nodeIndex)

      List.tabulate(numNodes) ({ x =>
        val key = ((nodeIndex + math.pow(2, x + 1)) % math.pow(2, buckets)).toInt
        val successorValue = arbitraryNode ? findSuccessor(key)
        val Val = Await.result(successorValue, timeout.duration).toString.toInt
        finger_table += (key -> Val)
      })

    case updateOthers(nodeIndex: Int) =>
      List.tabulate(buckets)(i => {
          val predecessorValue = findPredecessor((nodeIndex - math.pow(2, i)).toInt)
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
      dht += (data.id -> data.stockName)

    case getDataServer(nodeIndex: Int, m: Int) =>
      if(m == buckets){
        val fingerTBuffer = finger_table.toSeq
        List.tabulate(fingerTBuffer.size)(i=>
        if(belongs(nodeIndex, fingerTBuffer(i)._1, fingerTBuffer((i+1)%fingerTBuffer.size)._1)){
          if(fingerTBuffer(i)._2 != nodeIndex) {
            val successorNode = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH +
              fingerTBuffer(i)._2)
            successorNode ! getDataServer(id, m + 1)
          }
          else {
            val stockName = dht.get(nodeIndex)
            sender() ! nodeIndex + " " + stockName
          }
        })
      }
      sender() ! ""

    case getSnapshotServer() =>
      sender() ! finger_table
  }


  def getImmediateSuccessor(arbitraryNode: ActorSelection) : Int = {
    // findSuccessor logic
    val fingerNode = arbitraryNode ? node
    val fingerValue = Await.result(fingerNode, timeout.duration).toString.toInt
    val successorActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + finger_table(fingerValue))
    val successorNode = successorActor ? finger_table(0)
    Await.result(successorNode, timeout.duration).toString.toInt
  }

  def findSuccessor(id: Int) :Int = {
    val arbitraryNode :Int = findPredecessor(id)
    val successorActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + finger_table(arbitraryNode))
    getImmediateSuccessor(successorActor)
  }

  def findPredecessor(id: Int): Int ={
    var arbitraryNode =  node
    val arbitraryNodeActor = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + node)
    while(!belongs(id, arbitraryNode , getImmediateSuccessor(arbitraryNodeActor))){
      val arbitraryNodeFinger = arbitraryNodeActor ? closest_preceding_finger(id)
      arbitraryNode = Await.result(arbitraryNodeFinger, timeout.duration).toString.toInt
    }
    arbitraryNode
  }

  def closest_preceding_finger(id: Int): Int = {
    for (i <- (1 to m).reverse) {
      if(belongs( finger_table(i), node, id)){
        return finger_table(i)
      }
    }
    node
  }

}

object ServerActor {
  case class initializeFingerTable(nodeIndex: Int)
  case class initializeFirstFingerTable(nodeIndex: Int)
  case class updateFingerTable()
  case class getDataServer(nodeIndex: Int, m: Int)
  case class loadDataServer(data: EntityDefinition)
  case class findSuccessor(index: Int)
  case class updatePredecessor(nodeIndex: Int)
  case class updateOthers(nodeVal: Int)
  case class updateTable(predecessorValue: Int, nodeVal: Int, i: Int)
  case class getSnapshotServer()

}
