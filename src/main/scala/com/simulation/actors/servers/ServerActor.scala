package com.simulation.actors.servers
import akka.actor.Actor
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor.{findSuccessor, getPredecessor, initializeFingerTable, initializeFirstFingerTable, updateOthers, updatePredecessor, updateTable}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.FingerEntry

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerActor(id: Int, numNodes: Int) extends Actor {

  var dht = scala.collection.mutable.HashMap[Int, Int]()
  var finger_table = scala.collection.mutable.LinkedHashMap[Int, String]()
  var predecessor: String = _
  val timeout = Timeout(10 seconds)

  // Check if s belongs from n to fingerIthEntry
  def belongs(s:Int, n: Int, fingerIthEntry_int: Int): Boolean = {
    if(n > fingerIthEntry_int){
      (n until numNodes).foreach{
        i => if(i == s) return true
      }
      (0 until fingerIthEntry_int).foreach{
        i => if(i == s) return true
      }
    }
    else{
      (n until fingerIthEntry_int).foreach{
        i => if(i == s) return true
      }
    }




    false

  }

  override def receive = {

    case initializeFirstFingerTable(hash: String) =>
      val nodePath = self.path.toString
      List.tabulate(numNodes)(x => finger_table += (((hash.toInt + math.pow(2, x)) % math.pow(2, numNodes)).toInt -> nodePath))
      predecessor = nodePath

    case updatePredecessor(nodePath: String) =>
      predecessor = nodePath

    case getPredecessor() =>
      sender() ! predecessor

    case initializeFingerTable(hash: String, arbitraryPath: String) =>
      val firstKey = ((hash.toInt + math.pow(2, 0)) % math.pow(2, numNodes)).toInt
      val arbitraryNode = context.system.actorSelection(arbitraryPath)
      val future_firstVal = arbitraryNode ? findSuccessor(firstKey)
      val firstVal = Await.result(future_firstVal, timeout.duration).toString
      finger_table += (firstKey -> firstVal)
      val successor = context.system.actorSelection(finger_table(0))
      val future_predecessor = successor ? getPredecessor()
      predecessor = Await.result(future_predecessor, timeout.duration).toString
      successor ! updatePredecessor(self.path.toString)

      List.tabulate(numNodes) ({ x =>
        val key = ((hash.toInt + math.pow(2, x + 1)) % math.pow(2, numNodes)).toInt
        val future_Val = arbitraryNode ? findSuccessor(key)
        val Val = Await.result(future_Val, timeout.duration).toString
        finger_table += (key -> Val)
      })

    case updateOthers(nodeVal: Int) =>
      List.tabulate(numNodes)(i => {
          val future_p = self ? findSuccessor((nodeVal - math.pow(2, i)).toInt)
          val p_str = Await.result(future_p, timeout.duration).toString
          val p_obj = context.system.actorSelection(p_str)
          p_obj ! updateTable(nodeVal, i)
        })

      // p_obj -> n
      // nodeVal -> s
      // i -> i
    case updateTable(nodeVal: Int, i: Int) =>
      val n:Int = id
      val fingerIthEntry_str: String = finger_table(i)
      val fingerIthEntry_int: Int = fingerIthEntry_str.charAt(fingerIthEntry_str.length - 1)
      if(belongs(nodeVal, n, fingerIthEntry_int)){
        finger_table(i) = "akka://actor-system/user/server_actor_"+nodeVal
        val predObj = context.system.actorSelection(predecessor)
        predObj ! updateTable(nodeVal, i)
      }



  }

}

object ServerActor {
  case class initializeFingerTable(hash: String, path: String)
  case class initializeFirstFingerTable(hash: String)
  case class updateFingerTable()
  case class getData(id: Int)
  case class loadData(data: EntityDefinition)
  case class findSuccessor(start: Int)
  case class getPredecessor()
  case class updatePredecessor(nodePath: String)
  case class updateOthers(nodeVal: Int)
  case class updateTable(nodeVal: Int, i: Int)

}
