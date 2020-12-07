import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.simulation.ChordActorDriver
import org.scalatest.{Matchers, WordSpec}

class WebServiceTest extends WordSpec with ScalatestRouteTest with Matchers {

  val links: String = "<form action=\"http://localhost:8080/addNode\">\n    <input type=\"submit\" value=\"Add Node\" />\n</form>" +
    "<form action=\"http://localhost:8080/loadData\">\n    <input type=\"submit\" value=\"Load Data\" />\n</form>" +
    "<form action=\"http://localhost:8080/lookup\">\n    <input type=\"submit\" value=\"Lookup Data\" />\n</form>" +
    "<form action=\"http://localhost:8080/snapshot\">\n    <input type=\"submit\" value=\"Snapshot\" />\n</form>"

  val addNodeResponse = "Node added"

  val loadDataResponse = "Added: Id: 1, Name: You Will Meet a Tall Dark Stranger"

  val testRoute: Route = get {
    concat(
      path(""){
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          "<form action=\"http://localhost:8080/addNode\">\n    <input type=\"submit\" value=\"Add Node\" />\n</form>" +
            "<form action=\"http://localhost:8080/loadData\">\n    <input type=\"submit\" value=\"Load Data\" />\n</form>" +
            "<form action=\"http://localhost:8080/lookup\">\n    <input type=\"submit\" value=\"Lookup Data\" />\n</form>" +
            "<form action=\"http://localhost:8080/snapshot\">\n    <input type=\"submit\" value=\"Snapshot\" />\n</form>"))
      },

      path("addNode"){
        val result = ChordActorDriver.createServerNode()
        if(result)
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
            "Node added"
          ))
        else
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
            "Cant Add"
          ))
      },

      path("loadData"){
        val result = ChordActorDriver.loadData(1)
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          "Added: " + result
        ))
      },

      path("lookup"){
        val result = ChordActorDriver.getData(1)
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          "Lookup value: " + result
        ))

      }
    )
  }

  "Links" should {
    "be correct" in {
      Get() ~> testRoute ~> check {
        responseAs[String] shouldEqual links
      }
    }
  }

  "" should {
    "Node be added correctly" in{
      Get("/addNode") ~> testRoute ~> check {
        responseAs[String] shouldEqual addNodeResponse
      }
    }

    "Data loaded correctly" in {
      Get("/loadData") ~> testRoute ~> check {
        responseAs[String] shouldEqual loadDataResponse
      }
    }

    /*"Lookup data working correctly" in {
      Get("/lookup") ~> testRoute ~> check {
        println(responseAs[String])
        //responseAs[String] shouldEqual "Added: Id: 1, Name: You Will Meet a Tall Dark Stranger"
      }
    }*/
  }

}
