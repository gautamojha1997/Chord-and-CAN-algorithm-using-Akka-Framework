package com.simulation.actors.servers
import akka.actor.{Actor, ActorSystem, Props}
import com.simulation.beans.EntityDefinition

class ServerActor(id: Int, numNodes: Int) extends Actor {

  var dht = scala.collection.mutable.HashMap[Int, Int]()
  var finger_table = scala.collection.mutable.HashMap[Int, Int]()

  override def receive = {
    case _ => println("whatever")
  }
}

object ServerActor {
  case class initializeFingerTable()
  case class updateFingerTable()
  case class getData(id: Int)
  case class loadData(data: EntityDefinition)

}
