import UserActor.createUserActor
import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import scala.language.postfixOps

import scala.concurrent.duration.DurationInt

class UserActor(userId: Int, actorSystem: ActorSystem) extends Actor {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val timeout = Timeout(15 seconds)
  override def receive: Receive = {
    case createUserActor(id) =>
      val userActor = context.actorOf(Props(new UserActor(id, actorSystem)), "" + id)
      sender() ! userActor.path
  }
}


object UserActor {
  case class createUserActor(id:Int)
}


