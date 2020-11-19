package com.simulation.actors.users

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.alpakka.cassandra.{CassandraSessionSettings, CassandraWriteSettings}
import akka.stream.alpakka.cassandra.scaladsl.{CassandraFlow, CassandraSession, CassandraSessionRegistry, CassandraSource}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.datastax.driver.core.{Cluster, Session}
import com.datastax.oss.driver.api.core.cql.{BoundStatement, PreparedStatement}
import com.simulation.actors.supervisors.SupervisorActor.{getDataSupervisor, loadDataSupervisor}
import com.simulation.actors.users.UserActor.{createUserActor, getDataUserActor, loadData}
import com.simulation.beans.EntityDefinition
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class UserActor(userId: Int, actorSystem: ActorSystem) extends Actor{
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val timeout = Timeout(15 seconds)
  override def receive: Receive = {

    /**
     * Returns result of the loaded data from the server to the user.
     */
    case loadData(data) =>
      logger.info("Loading data using user actor")
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")
      val nextActor = supervisorActor ? loadDataSupervisor(data)
      val result = Await.result(nextActor, timeout.duration)
      sender() ! result

    /**
     * Returns result by looking up data from the server.
     */
    case getDataUserActor(id) =>
      logger.info("Fetching data from server actor")
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")
      val nextActor = supervisorActor ? getDataSupervisor(id)
      val result = Await.result(nextActor, timeout.duration)
      sender() ! result

    /**
     * Returns path of created user actor.
     */
    case createUserActor(id) =>
      val userActor = context.actorOf(Props(new UserActor(id, actorSystem)), "" + id)
      sender() ! userActor.path

  }
}

object UserActor {
  case class loadData(data:EntityDefinition)
  case class getDataUserActor(id:Int)
  case class createUserActor(id:Int)

}