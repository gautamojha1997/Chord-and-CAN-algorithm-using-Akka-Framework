package com.simulation.actors.can

import akka.actor.Actor
import com.simulation.actors.can.BootstrapActor._
import com.simulation.beans.EntityDefinition

class BootstrapActor extends Actor {
  override def receive: Receive = {

    case createServerActorCAN() => {

    }
    case getDataBootstrapCAN(id: Int) => {

    }
    case loadDataBootstrapCAN(data: EntityDefinition) => {

    }
    case getSnapshotCAN() => {

    }

  }
}

object BootstrapActor {
  case class createServerActorCAN()
  case class getDataBootstrapCAN(id: Int)
  case class loadDataBootstrapCAN(data: EntityDefinition)
  case class getSnapshotCAN()
}