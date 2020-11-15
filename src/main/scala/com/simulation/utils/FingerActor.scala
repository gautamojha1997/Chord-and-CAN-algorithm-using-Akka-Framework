package com.simulation.utils

import akka.actor.Actor
import com.simulation.utils.FingerActor.{fetchFingerTable, updateFingerTable}

import scala.collection.mutable

class FingerActor extends Actor{

  var finger_table = new Array[scala.collection.mutable.LinkedHashMap[Int, Int]](16)

  override def receive: Receive = {
    case updateFingerTable(finger: scala.collection.mutable.LinkedHashMap[Int, Int], nodeIndex: Int) =>
      finger_table(nodeIndex) = finger

    case fetchFingerTable(nodeIndex: Int) =>
      sender() ! finger_table(nodeIndex)

  }
}

object FingerActor {
  case class fetchFingerTable(nodeIndex: Int)
  case class updateFingerTable(finger: scala.collection.mutable.LinkedHashMap[Int, Int], nodeIndex: Int)
}
