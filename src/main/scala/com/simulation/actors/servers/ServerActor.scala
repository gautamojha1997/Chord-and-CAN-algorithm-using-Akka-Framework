package com.simulation.actors.servers
import akka.actor.{Actor, ActorSystem, Props}
import com.simulation.actors.servers.ServerActor.initializeFingerTable
import com.simulation.beans.EntityDefinition
import com.simulation.utils.FingerEntry

class ServerActor(id: Int, numNodes: Int) extends Actor {

  var dht = scala.collection.mutable.HashMap[Int, Int]()
  var finger_table = scala.collection.mutable.HashMap[Int, Int]()

  override def receive = {

    case initializeFingerTable(hash: String) =>
      List.tabulate(numNodes)(x => new FingerEntry(((hash.toInt + math.pow(2, x)) % math.pow(2, numNodes)).toInt, hash.toInt))
  }

}

object ServerActor {
  case class initializeFingerTable(hash: String)
  case class updateFingerTable()
  case class getData(id: Int)
  case class loadData(data: EntityDefinition)

}
