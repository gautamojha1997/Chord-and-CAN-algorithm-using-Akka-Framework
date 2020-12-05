package com.simulation.actors.chord.servers
import akka.actor.{Actor, ActorSelection, Props}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.chord.servers.ServerActor.{findSuccessor, getDataServer, getSnapshotServer, initializeFingerTable, initializeFirstFingerTable, loadDataServer, updateOthers, updateTable}
import com.simulation.beans.EntityDefinition
import org.slf4j.{Logger, LoggerFactory}
import com.simulation.utils.ApplicationConstants
import com.simulation.utils.FingerActor.{fetchData, fetchFingerTable, getFingerValue, getPredecessor, getSuccessor, setFingerValue, setPredecessor, setSuccessor, storeData, updateFingerTable}

import scala.collection.mutable
import scala.language.postfixOps
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ServerActor(id: Int, numNodes: Int) extends Actor {

  val fingerNode = context.system.actorSelection("akka://actorSystem/user/finger_actor")
  var finger_table = scala.collection.mutable.LinkedHashMap[Int, Int]()
  val timeout = Timeout(200 seconds)
  val buckets = (Math.log(numNodes)/Math.log(2.0)).toInt
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  var dht = mutable.HashMap[Int, String]()

  /**
   * Check if s belongs from n to successorValue
   * @param s
   * @param n
   * @param successorValue
   * @return s belongs or not
   */
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

  override def receive = {

    /**
     * It initializes the first finger table for the first server-node.
     */
    case initializeFirstFingerTable(nodeIndex: Int) =>
      List.tabulate(buckets)(x => finger_table +=
        (((nodeIndex + math.pow(2, x)) % math.pow(2, buckets)).toInt -> nodeIndex))
      logger.info(finger_table.toString)
      fingerNode ! setPredecessor(nodeIndex, nodeIndex)
      fingerNode ! setSuccessor(nodeIndex, nodeIndex)
      fingerNode ! updateFingerTable(finger_table, nodeIndex)

    /**
     * It initializes the finger table for all the server nodes after the first one.
     */
    case initializeFingerTable(nodeIndex: Int) =>
      val firstKey = ((id + math.pow(2, 0)) % numNodes).toInt
      val arbitraryNode = context.system.actorSelection(ApplicationConstants.SERVER_ACTOR_PATH + nodeIndex)
      logger.info(arbitraryNode.toString())
      val successorValue = arbitraryNode ? findSuccessor(firstKey)
      val firstVal = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
      finger_table += (firstKey -> firstVal)
      fingerNode ! setSuccessor(id, firstVal)
      val newPredecessor = fingerNode ? getPredecessor(firstVal)
      val newPredecessorR = Await.result(newPredecessor, timeout.duration).asInstanceOf[Int]
      fingerNode ! setPredecessor(id, newPredecessorR)
      fingerNode ! setPredecessor(newPredecessorR, id)

      logger.info("First Instance: " + finger_table.toString)

      (0 to buckets-2).foreach({ i =>
        val key = ((id + math.pow(2, i + 1)) % math.pow(2, buckets)).toInt
        val initialValue = finger_table.toSeq(i)._2
        if(belongs(key, id, initialValue)) {
          val successorValueR = initialValue
          finger_table += (key -> successorValueR)
        }
        else{
          val successorValue = arbitraryNode ? findSuccessor(key)
          val successorValueR = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
          finger_table += (key -> successorValueR)
        }
      })
      logger.info("Second Instance: " + finger_table.toString)
      fingerNode ! updateFingerTable(finger_table, id)


    /**
     * It updates all nodes whose finger table should refer to the new node which joins the network.
     */
    case updateOthers(activeNodes: mutable.TreeSet[Int]) =>
      activeNodes.add(id)
      val activeNodesList = activeNodes.toList
      (0 until activeNodesList.size).foreach { nodeIndex =>
        val fTable = fingerNode ? fetchFingerTable(activeNodesList(nodeIndex))
        val fTableR = Await.result(fTable, timeout.duration).asInstanceOf[mutable.LinkedHashMap[Int,Int]].toSeq
        (0 until buckets).foreach({ fingerValue =>
          if(belongs(id, fTableR(fingerValue)._1, fTableR((fingerValue+1)%buckets)._1+1)){
            fingerNode ! setFingerValue(activeNodesList(nodeIndex), fingerValue, id)
          }
        })
        logger.info("Checking Values of FingerTable"+fTableR.toString)
      }

    /**
     * Loads data to the server
     */
    case loadDataServer(data: EntityDefinition, nodeIndex: Int, hash: Int) =>
      sender() ! loadData(data: EntityDefinition, nodeIndex: Int, hash: Int)

    /**
     * Gets data from the server
     */
    case getDataServer(id: Int, hash: Int) =>
      val output = getData(id, hash)
      sender() ! output

    case getSnapshotServer() =>
      logger.info("In getsnapshot server")
      sender() ! finger_table

    /**
     * Returns successor value for the given node by fetching successor value for an arbitrary node and eventually updating the successor value for the given node.
     */
    case findSuccessor(nodeIndex: Int) =>
      logger.info("In find successor, node index = " + nodeIndex + " id = " + id)
      val arbitraryNode: Int = findPredecessor(nodeIndex)
      logger.info("In find successor, predecessor value = " + arbitraryNode)
      val successorValue = fingerNode ? getSuccessor(arbitraryNode)
      val successorValueR = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
      logger.info("Successor Found, value = " + successorValueR)
      sender() ! successorValueR
  }

  /**
   * Contains the main logic of loading the data
   * @param data
   * @param nodeIndex
   * @param hash
   * @return
   */
  def loadData(data: EntityDefinition, nodeIndex: Int, hash: Int): String = {
    var result = ""
    val fTable = fingerNode ? fetchFingerTable(id)
    val fTableR = Await.result(fTable, timeout.duration).asInstanceOf[mutable.LinkedHashMap[Int,Int]].toSeq
    (0 until buckets).foreach({ fingerValue =>
      if(belongs(hash, fTableR(fingerValue)._1, fTableR((fingerValue+1)%buckets)._1+1)){
        logger.info("Data stored at " + fTableR(fingerValue)._2)
        dht += (data.id -> data.name)
        fingerNode ! storeData(fTableR(fingerValue)._2, data)
        result = "Id: " + data.id + ", Name: " + data.name
        return result
      }
    })
    if(result == "")
      result = loadData(data, fTableR(buckets-1)._2, hash)
    result
  }

  /**
   * Contains the main logic of getting the data
   * @param nodeIndex
   * @param hash
   * @return
   */
  def getData(nodeIndex: Int, hash: Int) : String = {
    var movieNameR = ""
    val fTable = fingerNode ? fetchFingerTable(id)
    val fTableR = Await.result(fTable, timeout.duration).asInstanceOf[mutable.LinkedHashMap[Int,Int]].toSeq
    (0 until buckets).foreach({ fingerValue =>
      if(belongs(hash, fTableR(fingerValue)._1, fTableR((fingerValue+1)%buckets)._1+1)){
        val movieName = fingerNode ? fetchData(fTableR(fingerValue)._2, nodeIndex)
        movieNameR = Await.result(movieName, timeout.duration).toString
        logger.info("Data was stored at " + fTableR(fingerValue)._2)
        return nodeIndex+" "+movieNameR
      }
    })
    if(movieNameR == "")
      movieNameR = getData(fTableR(buckets-1)._2, hash)
    nodeIndex+" "+movieNameR
  }

  /**
   * Returns predecessor value for the given node by invoking another method closestPrecedingFinger(nodeIndex: Int) which returns finger table value for the given node.
   * @param nodeIndex
   * @return
   */
  def findPredecessor(nodeIndex: Int): Int ={
    logger.info("In find predecessor, node index = "+nodeIndex+" id = "+id)
    var arbitraryNode = id
    val successorValue = fingerNode ? getSuccessor(arbitraryNode)
    val successorValueR = Await.result(successorValue, timeout.duration).asInstanceOf[Int]
    while(!belongs(nodeIndex, arbitraryNode , successorValueR)){
      arbitraryNode =  closestPrecedingFinger(nodeIndex)
    }
    logger.info("Predecessor found, value = "+arbitraryNode)
    arbitraryNode
  }

  /**
   * Finds the closest preceding finger entry for the given nodeIndex
   * @param nodeIndex
   * @return
   */
  def closestPrecedingFinger(nodeIndex: Int): Int = {
    var closestIndex = id
    for (i <- (0 until buckets).reverse) {
      val fingerValue = fingerNode ? getFingerValue(id, i)
      val fingerValueR = Await.result(fingerValue, timeout.duration).asInstanceOf[Int]
      logger.info("In closest preceeding finger, value = "+fingerValueR+"")
      if(belongs(fingerValueR, id, nodeIndex)){
        logger.info("Found closest preceding finger, value = " + fingerValueR)
        closestIndex = fingerValueR
      }
    }
    logger.info("Found closest preceding finger, value = " + id)
    closestIndex
  }
}

object ServerActor {
  case class initializeFingerTable(nodeIndex: Int)
  case class initializeFirstFingerTable(nodeIndex: Int)
  case class getDataServer(nodeIndex: Int, hash:Int)
  case class loadDataServer(data: EntityDefinition, nodeIndex: Int, hash: Int)
  case class findSuccessor(index: Int)
  case class updateOthers(activeNodes: mutable.TreeSet[Int])
  case class updateTable(s: Int, i: Int)
  case class getSnapshotServer()
}