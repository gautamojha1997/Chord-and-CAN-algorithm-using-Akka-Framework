package com.simulation.actors.users

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.supervisors.SupervisorActor.{getDataSupervisor, loadDataSupervisor}
import com.simulation.actors.users.UserActor.{createUserActor, getDataUserActor, loadDataUserActor}
import com.simulation.beans.EntityDefinition
import com.simulation.utils.Data

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class UserActor(userId: Int, actorSystem: ActorSystem) extends Actor{
  val timeout = Timeout(5 seconds)
  override def receive: Receive = {
<<<<<<< HEAD

    case loadData(data) =>
=======
    case loadDataUserActor(data) =>
>>>>>>> origin/master
      val supervisorActor = actorSystem.actorSelection("akka://actorSystem/user/supervisor_actor")
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
<<<<<<< HEAD
  case class loadData(data:Data)
  case class getData(id:Int)
=======
  case class loadDataUserActor(data:EntityDefinition)
  case class getDataUserActor(id:Int)
>>>>>>> origin/master
  case class createUserActor(id:Int)
//
//
//  implicit val system = ActorSystem()
//  implicit val materializer = ActorMaterializer()
//  // Used for Future flatMap/onComplete/Done
//  implicit val executionContext = system.dispatcher
//
//  //creating Cluster object
//  val cluster = Cluster.builder.addContactPoint("127.0.0.1").build()
//
//  //Connect to the lambda_architecture keyspace
//  val cassandraConn = cluster.connect("testkeyspace")
//
//  def main(args: Array[String]) {
//
//    //Define a route with Get and POST
//    val route: Route =
//      get {
//        path("getAll" ) {
//          complete( cassandraReader("select JSON * from emp")   )
//        }
//      }~ post {
//        path("insertData") {
//          entity(as[loadData]) { emp =>
//            val saved: Future[Done] = cassandraWriter(emp)
//            onComplete(saved) { done => complete("Data inserted !!!n")}
//          }
//        }
//      }
//
//    //Binding to the host and port
//    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
//    println(s"Server online at http://localhost:8080/nPress Enter to stop...")
//    StdIn.readLine() // let the server run until user presses Enter
//
//    bindingFuture
//      .flatMap(_.unbind()) // trigger unbinding from the port
//      .onComplete(_ ⇒ system.terminate()) // and shutdown when done
//
//    //Close cassandra connection
//    cluster.close()
//
//  }
//
//
//  /**
//   * Read the Cassandra Data and convert each Row to Employee object
//   * @param query the query to execute
//   * @return the list of Employee
//   */
//  def cassandraReader(query: String):List[loadData] = {
//    var empList:List[loadData] = Nil
//
//    //Get the result set from query execution
//    val resultSet= cassandraConn.execute(query)
//
//    //Get the iterator of the result set
//    val it=resultSet.iterator()
//    while(it.hasNext)
//    {
//      //Convert each row of json data to Employee object
//      val jsonString=resultSet.one().getString(0)
//      val jsonObj=jsonString.parseJson.convertTo[loadData]
//
//      //Add to empList
//      empList=  jsonObj :: empList
//    }
//
//    return empList
//  }
//
//  /**
//   * Write data into Cassandra Database
//   * @param emp the detail of Employee
//   * @return Future Done
//   */
//  def cassandraWriter(emp:loadData)={
//
//    //Insert data into the table if it does not exist
//    var query="INSERT INTO emp (emp_id, emp_name, emp_city,emp_phone, emp_sal)n" +
//      s"VALUES(${emp.emp_id},'${emp.emp_name}', '${emp.emp_city}', ${emp.emp_phone}, ${emp.emp_sal}) IF NOT EXISTS;";
//
//    //Execute the query
//    cassandraConn.execute(query)
//
//    //return Future Done
//    Future { Done }
//  }
}