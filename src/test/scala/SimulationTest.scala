import akka.actor.{ActorPath, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.simulation.actors.supervisors.SupervisorActor
import org.scalatest.{BeforeAndAfter, FlatSpecLike, MustMatchers, stats}

class SimulationTest extends TestKit(ActorSystem("actorSystemTest")) with FlatSpecLike with BeforeAndAfter with MustMatchers {

  "User Actor object after creation" should "not be null" in {
    val sender = TestProbe()
    val id: Int = 999
    val actorSystem = ActorSystem("actorSystem")
    val userActor = actorSystem.actorOf(Props(new UserActor(id, actorSystem)), "user_actor")
    sender.send(userActor, UserActor.createUserActor(id))
    val state = sender.expectMsgType[ActorPath]
    assert(state != null)
  }

  "User Actor object" should "be correctly created" in {
    val sender = TestProbe()
    val id: Int = 999
    val actorSystem = ActorSystem("actorSystem")
    val userActor = actorSystem.actorOf(Props(new UserActor(id, actorSystem)))
    sender.send(userActor, UserActor.createUserActor(id))
    val state = sender.expectMsgType[ActorPath]
    assert(state.name == id.toString && userActor.path + "/" + id.toString == state.address + "/" + state.elements.mkString("/"))
  }

}
