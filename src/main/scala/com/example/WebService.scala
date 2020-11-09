package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, concat, get, parameters, path, pathSingleSlash, post, put}
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object WebService {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

    val route =

      get {
        concat(

          path(""){
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "<p>To perform load and lookup, you need to search for http://localhost:8080/load?id=yourId and http://localhost:8080/lookup?id=yourId respectively </p>" +
                "<form action=\"http://localhost:8080/addNode\">\n    <input type=\"submit\" value=\"Add node\" />\n</form>" +
                "<form action=\"http://localhost:8080/snapshot\">\n    <input type=\"submit\" value=\"Snapshot\" />\n</form>"))
          },

          path("addNode"){
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "Node added"
            ))
          },

          path("snapshot"){
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              "Snapshot created"
            ))
          },

          path("load"){
            parameters("id"){
              (id) =>
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "Added: " + id
              ))
            }
          },

          path("lookup"){
            parameters("id"){
              id =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                  "Lookup value: " + id
                ))
            }
          }
        )
      }


    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
