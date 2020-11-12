package com.simulation.actors.servers
import akka.actor.{Actor, ActorSelection}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor.{findSuccessor, initializeFingerTable, initializeFirstFingerTable, updateOthers, updatePredecessor, updateTable}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.FingerEntry

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerActor(id: Int, numNodes: Int) extends Actor {

  var dht = scala.collection.mutable.HashMap[Int, Int]()
  var finger_table = scala.collection.mutable.LinkedHashMap[Int, Int]()
  var predecessor: Int = _
  val timeout = Timeout(10 seconds)
  val buckets = (Math.log(numNodes)/Math.log(2.0)).toInt
  val SERVER_ACTOR_PATH = "akka://actor-system/user/server_actor_"
  var node: Int = _

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

    case initializeFirstFingerTable(hash: String) =>
      List.tabulate(buckets)(x => finger_table += (((hash.toInt + math.pow(2, x)) % math.pow(2, buckets)).toInt -> hash.toInt))
      predecessor = hash.toInt

    case updatePredecessor(nodeIndex: Int) =>
      predecessor = nodeIndex

    case initializeFingerTable(hash: String, nodeIndex: Int) =>

      val firstKey = ((hash.toInt + math.pow(2, 0)) % math.pow(2, buckets)).toInt
      val arbitraryNode = context.system.actorSelection(SERVER_ACTOR_PATH + nodeIndex)
      val successorValue = arbitraryNode ? findSuccessor(firstKey)
      val firstVal = Await.result(successorValue, timeout.duration).toString.toInt
      finger_table += (firstKey -> firstVal)
      val successor = context.system.actorSelection(SERVER_ACTOR_PATH + finger_table(0))
      // check for successor or cuurent node
//      val futurePredecessor = findPredecessor(nodeIndex)
      successor ! updatePredecessor(nodeIndex)

      List.tabulate(numNodes) ({ x =>
        val key = ((hash.toInt + math.pow(2, x + 1)) % math.pow(2, buckets)).toInt
        val successorValue = arbitraryNode ? findSuccessor(key)
        val Val = Await.result(successorValue, timeout.duration).toString.toInt
        finger_table += (key -> Val)
      })

    case updateOthers(nodeIndex: Int) =>
      List.tabulate(buckets)(i => {
          val predecessorValue = findPredecessor((nodeIndex - math.pow(2, i)).toInt)
          val predecessorObject = context.system.actorSelection(SERVER_ACTOR_PATH + predecessorValue)
          predecessorObject ! updateTable(predecessorValue, nodeIndex, i)
        })

      // predecessorValue -> n
      // nodeIndex -> s
      // i -> i
    case updateTable(predecessorValue:Int, nodeIndex: Int, i: Int) =>
      if(belongs(nodeIndex, predecessorValue, finger_table(i))){
        finger_table(i) = nodeIndex
        val predObj = context.system.actorSelection(SERVER_ACTOR_PATH + predecessor)
        predObj ! updateTable(predecessor, nodeIndex, i)
      }
  }


  def getImmediateSuccessor(arbitraryNode: Int) : Int = {
    // findSuccessor logic
    val successorActor = context.system.actorSelection(SERVER_ACTOR_PATH + finger_table(arbitraryNode))
    val successorNode = successorActor ? finger_table(0)
    Await.result(successorNode, timeout.duration).toString.toInt
  }

  def findSuccessor(id: Int) :Int = {
    val arbitraryNode :Int = findPredecessor(id)
    val successorActor = context.system.actorSelection(SERVER_ACTOR_PATH + finger_table(arbitraryNode))
    getImmediateSuccessor(successorActor)
  }

  def findPredecessor(id: Int): Int ={
    var arbitraryNode =  node
    val arbitraryNodeActor = context.system.actorSelection(SERVER_ACTOR_PATH + node)
    while(!belongs(id, arbitraryNode , getImmediateSuccessor(arbitraryNodeActor))){
      arbitraryNode = arbitraryNodeActor ? closest_preceding_finger(id)
    }
    arbitraryNode
  }

  def closest_preceding_finger(id: Int): Int = {
    val m = (Math.log(numNodes)/Math.log(2)).toInt
    for (i <- (1 to m).reverse) {}
    n
  }

}

object ServerActor {
  case class initializeFingerTable(hash: String, nodeIndex: Int)
  case class initializeFirstFingerTable(hash: String)
  case class updateFingerTable()
  case class getData(id: Int)
  case class loadData(data: EntityDefinition)
  case class findSuccessor(index: Int)
  case class updatePredecessor(nodeIndex: Int)
  case class updateOthers(nodeVal: Int)
  case class updateTable(predecessorValue: Int, nodeVal: Int, i: Int)

}
