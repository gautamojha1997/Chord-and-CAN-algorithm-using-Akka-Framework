package com.simulation.actors.servers
import akka.actor.Actor
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.servers.ServerActor.{findSuccessor, getPredecessor, initializeFingerTable, initializeFirstFingerTable, updatePredecessor}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.FingerEntry

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerActor(id: Int, numNodes: Int) extends Actor {

  var dht = scala.collection.mutable.HashMap[Int, Int]()
  var finger_table = scala.collection.mutable.HashMap[Int, String]()
  var predecessor: String = _
  val timeout = Timeout(10 seconds)

  override def receive = {

    case initializeFirstFingerTable(hash: String) => {
      val nodePath = self.path.toString
      List.tabulate(numNodes)(x => finger_table += (((hash.toInt + math.pow(2, x)) % math.pow(2, numNodes)).toInt -> nodePath))
      predecessor = nodePath
    }

    case updatePredecessor(nodePath: String) =>
      predecessor = nodePath

    case getPredecessor() =>
      sender() ! predecessor

    case initializeFingerTable(hash: String, arbitraryPath: String) => {
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

}
