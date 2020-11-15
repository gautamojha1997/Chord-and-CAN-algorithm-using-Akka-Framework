package com.simulation

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, concat, get, parameters, path, pathSingleSlash, post, put}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object WebService {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    implicit val executionContext = system.executionContext
    val logger: Logger = LoggerFactory.getLogger(this.getClass)

    val route =

      get {
        concat(

          path(""){
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "<p>To perform load and lookup, you need to search for http://localhost:8080/load?id=yourId and http://localhost:8080/lookup?id=yourId respectively </p>" +
                "<form action=\"http://localhost:8080/addNode\">\n    <input type=\"submit\" value=\"Add Node\" />\n</form>" +
                "<form action=\"http://localhost:8080/loadData\">\n    <input type=\"submit\" value=\"Load Data\" />\n</form>" +
                "<form action=\"http://localhost:8080/lookup\">\n    <input type=\"submit\" value=\"Lookup Data\" />\n</form>" +
                "<form action=\"http://localhost:8080/snapshot\">\n    <input type=\"submit\" value=\"Snapshot\" />\n</form>"))
          },

          path("addNode"){
            val result = ActorDriver.createServerNode()
            if(result)
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "Node added"
            ))
            else
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Cant Add"
              ))
          },

          path("snapshot"){
            logger.info("Snapshot Web Service")
            val result = ActorDriver.printSnapshot()
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "Snapshot created"
            ))
          },

          path("loadData"){
            logger.info("In loadData webservice")
            parameters("id"){
              (id) =>
                val result = ActorDriver.loadData(id.toInt)
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                  "Added: " + result
                ))
            }
          },

          path("lookup"){
            parameters("id"){
              id =>
                ActorDriver.getData(id.toInt)
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                  "Lookup value: " + id
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
