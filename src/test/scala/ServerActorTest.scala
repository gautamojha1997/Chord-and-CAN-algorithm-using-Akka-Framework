import akka.actor.Actor
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer

class ServerActorTest(id: Int, numNodes: Int) {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  // Check if s belongs from n to fingerIthEntry
  def belongs(s:Int, n: Int, successorValue: Int): Boolean = {
    logger.info("Checking if "+s+ " belongs in the range "+n+" - "+successorValue)
    val nodeRanges:ListBuffer[Int] = if(n >= successorValue){
      val temp = ListBuffer.range(n,numNodes)
      temp.addAll(ListBuffer.range(0,successorValue))
    } else{
      val temp = ListBuffer.range(n,successorValue)
      temp
    }
    if(nodeRanges.contains(s))
      return true
    false
  }

}
