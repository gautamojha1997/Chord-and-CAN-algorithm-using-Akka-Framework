package com.simulation.utils

import akka.actor.Actor
import com.simulation.utils.FingerActor.{fetchFingerTable, getPredecessor, getSuccessor, setPredecessor, setSuccessor, updateFingerTable}

import scala.collection.mutable

class FingerActor extends Actor{

  var finger_table = new Array[scala.collection.mutable.LinkedHashMap[Int, Int]](16)
  var successor = new Array[Int](16)
  var predecessor = new Array[Int](16)


  override def receive: Receive = {
    case updateFingerTable(finger: scala.collection.mutable.LinkedHashMap[Int, Int], nodeIndex: Int) =>
      finger_table(nodeIndex) = finger

    case fetchFingerTable(nodeIndex: Int) =>
      sender() ! finger_table(nodeIndex)

    case getSuccessor(nodeIndex: Int) =>
      sender() ! successor(nodeIndex)

    case getPredecessor(nodeIndex: Int) =>
      sender() ! predecessor(nodeIndex)

    case setSuccessor(nodeIndex: Int) =>
      sender() ! successor(nodeIndex)

    case setPredecessor(nodeIndex: Int) =>
      sender() ! predecessor(nodeIndex)
  }
}

object FingerActor {
  case class fetchFingerTable(nodeIndex: Int)
  case class updateFingerTable(finger: scala.collection.mutable.LinkedHashMap[Int, Int], nodeIndex: Int)
  case class getSuccessor(nodeIndex: Int)
  case class getPredecessor(nodeIndex: Int)
  case class setSuccessor(nodeIndex: Int)
  case class setPredecessor(nodeIndex: Int)
}
