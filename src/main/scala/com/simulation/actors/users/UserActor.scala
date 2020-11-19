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

    case loadData(data) =>
      logger.info("In loadData UserActor")
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")

      //creating Cluster object
//      val cluster = Cluster.builder.addContactPoint("127.0.0.1").build()
//
//      //Connect to the lambda_architecture keyspace
//      val cassandraConn: Session = cluster.connect()
//
//      var query=  "CREATE TABLE stock(n" +
//        "id int PRIMARY KEY,n" +
//        "name text,n" +
//        ");"

      //cassandraConn.execute(query)

//      query="INSERT INTO stock (id, name)n" +
        "VALUES(${data.id},'${data.name});"

      // cassandraConn.execute(query)

//      query = "SELECT * FROM stock;"
//stock
      //val res = cassandraConn.execute(query)

      //print(res)
      val materializer: Materializer = ActorMaterializer.create(actorSystem)

      val place = EntityDefinition(1, "hi")
      val source = Source.single(place)
      val statementBinder: (EntityDefinition, PreparedStatement) => BoundStatement =
        (elemToInsert, preparedStatement) => preparedStatement.bind(elemToInsert.id, elemToInsert.name)

      val sessionSettings = CassandraSessionSettings()

      implicit val cassandraSession: CassandraSession = CassandraSessionRegistry.get(actorSystem).sessionFor(sessionSettings)
//
//      val query=  "CREATE TABLE test(n" +
//        "id int PRIMARY KEY,n" +
//        "name text,n" +
//        ");";
//      cassandraSession.executeDDL()
//
//      val flow = CassandraFlow.create(CassandraWriteSettings.defaults,
//        "INSERT INTO test(id, name) VALUES (?, ?)",
//        statementBinder)(cassandraSession)
//
//      val written = source.via(flow).runWith(Sink.head)(materializer)
//      val writtenR = Await.result(written, timeout.duration)
//      print(written)
      val rows =
        CassandraSource(s"SELECT * FROM test").map(_.getInt("id")).runWith(Sink.head)(materializer)
      val rowsR =  Await.result(rows,timeout.duration)
      print(rowsR)

      val nextActor = supervisorActor ? loadDataSupervisor(data)
      val result = Await.result(nextActor, timeout.duration)
      sender() ! result

    case getDataUserActor(id) =>
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")
      val nextActor = supervisorActor ? getDataSupervisor(id)
      val result = Await.result(nextActor, timeout.duration)
      sender() ! result

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