import UtilityTest.md5
import akka.actor.{ActorPath, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.simulation.actors.supervisors.SupervisorActor
import com.simulation.beans.EntityDefinition
import org.scalatest.{BeforeAndAfter, FlatSpecLike, MustMatchers, stats}

import scala.collection.mutable.ListBuffer
import scala.io.Source

class SimulationTest extends TestKit(ActorSystem("actorSystemTest")) with FlatSpecLike with BeforeAndAfter with MustMatchers {

  "Hash" should "be generated properly" in {
    assert(md5("test", 3) < math.pow(2, 3))
  }

  "User Actor object after creation" should "not be null" in {
    val sender = TestProbe()
    val id: Int = 999
    val actorSystem = ActorSystem("actorSystem")
    val userActor = actorSystem.actorOf(Props(new UserActorTest(id, actorSystem)), "user_actor")
    sender.send(userActor, UserActorTest.createUserActor(id))
    val state = sender.expectMsgType[ActorPath]
    assert(state != null)
  }

  "User Actor object" should "be correctly created" in {
    val sender = TestProbe()
    val id: Int = 999
    val actorSystem = ActorSystem("actorSystem")
    val userActor = actorSystem.actorOf(Props(new UserActorTest(id, actorSystem)))
    sender.send(userActor, UserActorTest.createUserActor(id))
    val state = sender.expectMsgType[ActorPath]
    assert(state.name == id.toString && userActor.path + "/" + id.toString == state.address + "/" + state.elements.mkString("/"))
  }

  "6" should "belong in range 3 to 0" in {
    val id: Int = 999
    val serverActor = new ServerActorTest(id, 15)
    assert(serverActor.belongs(1,3,2))
    assert(serverActor.belongs(4,0,7))
  }
}
