package com.simulation

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, concat, get, parameters, path, pathSingleSlash, post, put}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.ddahl.rscala.RClient
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object WebService {
  def main(args: Array[String]): Unit = {

    var nodeAdded: Boolean = false
    var monteNodeAdded: Boolean = false
    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    implicit val executionContext = system.executionContext
    val logger: Logger = LoggerFactory.getLogger(this.getClass)
    val rClient = RClient()

    val route =

      get {
        concat(
          path(""){
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "<form action=\"http://localhost:8080/chord\">\n    <input type=\"submit\" value=\"Chord\" />\n</form>" +
                "<form action=\"http://localhost:8080/can\">\n    <input type=\"submit\" value=\"CAN\" />\n</form>"))
          },

          path("can"){
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "<form action=\"http://localhost:8080/addNodeCAN\">\n    <input type=\"submit\" value=\"Add Node\" />\n</form>" +
                "<form action=\"http://localhost:8080/loadDataCAN\">\n    <input type=\"submit\" value=\"Load Data\" />\n</form>" +
                "<form action=\"http://localhost:8080/lookupCAN\">\n    <input type=\"submit\" value=\"Lookup Data\" />\n</form>" +
                "<form action=\"http://localhost:8080/snapshotCAN\">\n    <input type=\"submit\" value=\"Snapshot\" />\n</form>" ))
          },
          path("chord"){
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "<form action=\"http://localhost:8080/addNode\">\n    <input type=\"submit\" value=\"Add Node\" />\n</form>" +
                "<form action=\"http://localhost:8080/loadData\">\n    <input type=\"submit\" value=\"Load Data\" />\n</form>" +
                "<form action=\"http://localhost:8080/lookup\">\n    <input type=\"submit\" value=\"Lookup Data\" />\n</form>" +
                "<form action=\"http://localhost:8080/snapshot\">\n    <input type=\"submit\" value=\"Snapshot\" />\n</form>" +
                  "<form action=\"http://localhost:8080/montecarlo\">\n    <input type=\"submit\" value=\"montecarlo\" />\n</form>"))
          },

          path("addNodeCAN"){
            val result = CANActorDriver.createServerNodeCAN()
            if(result){
              nodeAdded = true
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Node added"
              ))
            }
            else
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Cant Add"
              ))
          },


          // If this path is received, simply returns the results for the simulation.
          path("snapshotCAN"){
            logger.info("Snapshot Web Service")
            if(nodeAdded){
              val result = CANActorDriver.printSnapshot()
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "<html><body>Snapshot created<br>"+ result +"</body></html>"
              ))
            }
            else{
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Please add a node first"
              ))
            }
          },

          path("removeNodeCAN"){
            logger.info("In removeNode webservice")
            parameters("id"){
              (id) =>
                if(nodeAdded){
                  val result = CANActorDriver.removeNode(id.toInt)
                    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Node removed: " + id))
                }
                else{
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Please add a node first"
                  ))
                }

            }
          },

          // If this path is received, loadData(id.toInt) is called which loads the result in the form of string in the server.
          path("loadDataCAN"){
            logger.info("In loadData webservice")
            parameters("id"){
              (id) =>
                if(nodeAdded){
                  val result = CANActorDriver.loadData(id.toInt)
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Added: " + result
                  ))
                }
                else{
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Please add a node first"
                  ))
                }

            }
          },

          // If this path is received, getData(id.toInt) is called which is used by the user to look data over a server node.
          path("lookupCAN"){
            parameters("id"){
              id =>
                if(nodeAdded){
                  val result = CANActorDriver.getData(id.toInt)
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Lookup value: " + result
                  ))
                }
                else{
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Please add a node first"
                  ))
                }

            }
          },

          // If this path is received, createServerNode() is called which adds a node.
          path("addNode"){
            val result = ChordActorDriver.createServerNode()
            if(result){
              nodeAdded = true
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Node added"

              ))
            }
            else
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Cant Add"
              ))
          },

          // If this path is received, simply returns the results for the simulation.
          path("snapshot"){
            logger.info("Snapshot Web Service")
            if(nodeAdded){
              val result = ChordActorDriver.printSnapshot()
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "<html><body>Snapshot created<br>"+ result +"</body></html>"
              ))
            }
            else{
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Please add a node first"
              ))
            }
          },

          // If this path is received, loadData(id.toInt) is called which loads the result in the form of string in the server.
          path("loadData"){
            logger.info("In loadData webservice")
            parameters("id"){
              (id) =>
                if(nodeAdded){
                  val result = ChordActorDriver.loadData(id.toInt)
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Added: " + result
                  ))
                }
                else{
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Please add a node first"
                  ))
                }

            }
          },

          // If this path is received, getData(id.toInt) is called which is used by the user to look data over a server node.
          path("lookup"){
            parameters("id"){
              id =>
                if(nodeAdded){
                  val result = ChordActorDriver.getData(id.toInt)
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Lookup value: " + result
                  ))
                }
                else{
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                    "Please add a node first"
                  ))
                }

            }
          },

          // If this path is received, Rclient object is invoked to randomly select the above 4 options
          path("montecarlo"){
            val idList = new ListBuffer[Int]()
            parameters("number"){
              number =>
                val toPrint = new StringBuilder()
                List.tabulate(number.toInt)(x => {
                  val choice = rClient.evalI0("runif(%-, %-, %-)", 1, 1, 5)
                  logger.info("choice = "+ choice.toString)
                  if(choice == 1){
                    toPrint.append("1.AddNode: ")
                    if(ChordActorDriver.createServerNode()) {
                      toPrint.append("NodeAdded")
                      monteNodeAdded = true
                    }
                    else {
                      toPrint.append("NodeNotAdded")
                    }
                  }
                  else if(choice == 2){
                    toPrint.append("2.Snapshot: ")
                    if(monteNodeAdded)
                      toPrint.append(ChordActorDriver.printSnapshot().toString)
                    else
                      toPrint.append("Create a node first")
                  }
                  else if(choice == 3){
                    toPrint.append("3.LoadData")
                    if(monteNodeAdded){
                      val id = rClient.evalI0("runif(%-, %-, %-)", 1, 0, ChordActorDriver.movieData.size - 1)
                      toPrint.append("("+id+"): ")
                      toPrint.append(ChordActorDriver.loadData(id))
                      idList += id
                    }
                    else
                      toPrint.append("Create a node first")
                  }
                  else if(choice == 4){
                    toPrint.append("4.LookupData")
                    if(monteNodeAdded){
                      val id = rClient.evalI0("runif(%-, %-, %-)", 1, 0, idList.size - 1)
                      toPrint.append("("+id+"): ")
                      toPrint.append(ChordActorDriver.getData(id))
                    }
                    else
                      toPrint.append("Create a node first")
                  }
                  toPrint.append("\n")
                  logger.info(rClient.evalI0("runif(%-, %-, %-)", 1, 1, 3).toString)
                })
                logger.info(toPrint.toString())
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                  toPrint.toString()
                ))
            }
          }

        )
      }


    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

}
