import UserActorTest.createUserActor
import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import scala.language.postfixOps

import scala.concurrent.duration.DurationInt

class UserActorTest(userId: Int, actorSystem: ActorSystem) extends Actor {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val timeout = Timeout(15 seconds)
  override def receive: Receive = {
    case createUserActor(id) =>
      val userActor = context.actorOf(Props(new UserActorTest(id, actorSystem)), "" + id)
      sender() ! userActor.path
  }
}


object UserActorTest {
  case class createUserActor(id:Int)
}


