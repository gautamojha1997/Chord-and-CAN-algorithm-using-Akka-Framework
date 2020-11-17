package com.simulation.utils

import java.util.ArrayList

import akka.actor.Actor
import com.simulation.utils.FingerActor.{fetchFingerTable, getFingerValue, getPredecessor, getSuccessor, setFingerValue, setPredecessor, setSuccessor, storeData, updateFingerTable}

import scala.collection.mutable

class FingerActor extends Actor{

  var fingerTable = new Array[mutable.LinkedHashMap[Int, Int]](16)
  var successor = new Array[Int](16)
  var predecessor = new Array[Int](16)
  var stockData = new Array[ArrayList[mutable.HashMap[Int, String]]](16)

  override def receive: Receive = {
    case updateFingerTable(finger: scala.collection.mutable.LinkedHashMap[Int, Int], nodeIndex: Int) =>
      fingerTable(nodeIndex) = finger

    case fetchFingerTable(nodeIndex: Int) =>
      sender() ! fingerTable(nodeIndex)

    case getFingerValue(nodeIndex: Int, index: Int) =>
      sender() ! fingerTable(nodeIndex).toSeq(index)._2

    case setFingerValue(nodeIndex: Int, index: Int, value:Int) =>
      val key = fingerTable(nodeIndex).toSeq(index)._1
      fingerTable(nodeIndex).put(key, value)

    case getSuccessor(nodeIndex: Int) =>
      sender() ! successor(nodeIndex)

    case getPredecessor(nodeIndex: Int) =>
      sender() ! predecessor(nodeIndex)

    case setSuccessor(nodeIndex: Int, value: Int) =>
      successor(nodeIndex) = value

    case setPredecessor(nodeIndex: Int, value: Int) =>
      predecessor(nodeIndex) = value

    case storeData(nodeIndex: Int, dht: mutable.HashMap[Int, String]) =>
      stockData(nodeIndex).add(dht)
  }
}

object FingerActor {
  case class fetchFingerTable(nodeIndex: Int)
  case class updateFingerTable(finger: scala.collection.mutable.LinkedHashMap[Int, Int], nodeIndex: Int)
  case class getFingerValue(nodeIndex: Int, index: Int)
  case class setFingerValue(nodeIndex: Int, index: Int, value:Int)
  case class getSuccessor(nodeIndex: Int)
  case class getPredecessor(nodeIndex: Int)
  case class setSuccessor(nodeIndex: Int, value: Int)
  case class setPredecessor(nodeIndex: Int, value: Int)
  case class fetchData(nodeIndex: Int, key: Int)
  case class storeData(nodeIndex: Int, dht: mutable.HashMap[Int, String])
}
