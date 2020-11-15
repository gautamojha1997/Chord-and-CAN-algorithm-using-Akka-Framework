import akka.actor.Actor

import scala.collection.mutable.ListBuffer

class ServerActorTest(id: Int, numNodes: Int) {
  // Check if s belongs from n to fingerIthEntry
  def belongs(s:Int, n: Int, successorValue: Int): Boolean = {
    val nodeRanges:ListBuffer[Int] = if(n >= successorValue){
      //check if inclusive
      val temp = ListBuffer.range(n,numNodes)
      temp.addAll(ListBuffer.range(0,successorValue))
    } else{
      val temp = ListBuffer.range(successorValue,n)
      temp
    }
    if(nodeRanges.contains(s))
      return true
    false
  }

}
