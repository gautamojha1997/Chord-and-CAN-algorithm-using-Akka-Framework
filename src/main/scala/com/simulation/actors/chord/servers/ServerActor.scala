package com.simulation.actors.chord.servers
import akka.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern.ask
import akka.remote.transport.ActorTransportAdapter.AskTimeout
import akka.util.Timeout
import com.simulation.actors.chord.servers.ServerActor.{getDataServer, initializeFingerTable, initializeFirstFingerTable, loadDataServer, removeNodeServer, updateOthers, updateTable}
import com.simulation.beans.EntityDefinition
import org.slf4j.{Logger, LoggerFactory}
import com.simulation.utils.FingerActor.{containsData, extendData, fetchData, fetchDataValue, fetchFingerTable, getFingerValue, getPredecessor, getSuccessor, setFingerValue, setPredecessor, setSuccessor, storeData, updateFingerTable}
import com.simulation.utils.Utility.md5
import com.typesafe.config.ConfigFactory

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
    case initializeFingerTable(nodeIndex: Int, activeNodes: mutable.TreeSet[Int]) =>
      val activeNodesList = activeNodes.toList
      (0 until buckets).foreach { entry =>
        finger_table +=  (((nodeIndex + math.pow(2, entry)) % math.pow(2, buckets)).toInt ->
          findSuccessor(nodeIndex, activeNodesList, entry))
      }
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
      sender() ! " "

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

    /**
     * Removes node from chord, updates finger table of other nodes and transfer data to proper node
     * */
    case removeNodeServer(activeNodes: mutable.TreeSet[Int]) =>
      logger.info("Removing node with index = " + id)
      fingerNode ! updateFingerTable(null, id)
      activeNodes.remove(id)
      val activeNodesList = activeNodes.toList
      (0 until activeNodesList.size).foreach { nodeIndex =>
        (0 until buckets).foreach({ fingerIndex =>
          val fingerValue = fingerNode ? getFingerValue(activeNodesList(nodeIndex), fingerIndex)
          val fingerValueR = Await.result(fingerValue, timeout.duration).asInstanceOf[Int]
          if(fingerValueR == id) {
            fingerNode ! setFingerValue(activeNodesList(nodeIndex), fingerIndex, activeNodesList(nodeIndex))
          }
        })
      }
      val checkData = fingerNode ? containsData(id)
      val checkDataR = Await.result(checkData, timeout.duration).asInstanceOf[Boolean]
      if(checkDataR && activeNodes.size > 0)
        extendDHT(activeNodes)
      sender() ! true
  }

  def findSuccessor(nodeIndex:Int, activeNodesList:List[Int], entry:Int): Int ={
    val firstVal = ((nodeIndex + math.pow(2, entry)) % numNodes).asInstanceOf[Int]
    val secondVal = ((nodeIndex + math.pow(2, (entry+1) % buckets)) % numNodes).asInstanceOf[Int]
    (0 until activeNodesList.size).foreach { index =>
      if(belongs(activeNodesList(index), firstVal, secondVal+1))
        return activeNodesList(index)
    }
    nodeIndex
  }

  /**
   * Transfer data to another node
   * @param activeNodes
   * @return
   */

  def extendDHT(activeNodes: mutable.TreeSet[Int]): Unit = {
    val dht = fingerNode ? fetchData(id)
    val dhtR = Await.result(dht, timeout.duration).asInstanceOf[mutable.HashMap[Int, String]]
    val fTable = fingerNode ? fetchFingerTable(activeNodes.head)
    val fTableR = Await.result(fTable, timeout.duration).asInstanceOf[mutable.LinkedHashMap[Int, Int]].toSeq
    val hash = md5(dhtR.toSeq(0)._1.toString, numNodes) % numNodes
    (0 until buckets).foreach({ fingerValue =>
      if (belongs(hash, fTableR(fingerValue)._1, fTableR((fingerValue + 1) % buckets)._1 + 1)) {
        logger.info("Data stored at " + fTableR(fingerValue)._2)
        fingerNode ! extendData(fTableR(fingerValue)._2, dhtR)
        return
      }
    })
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
        val movieName = fingerNode ? fetchDataValue(fTableR(fingerValue)._2, nodeIndex)
        movieNameR = Await.result(movieName, timeout.duration).toString
        logger.info("Data was stored at " + fTableR(fingerValue)._2)
        return nodeIndex+" "+movieNameR
      }
    })
    if(movieNameR == "")
      movieNameR = getData(fTableR(buckets-1)._2, hash)
    nodeIndex+" "+movieNameR
  }
}

object ServerActor {

  def props(id: Int, numNodes: Int): Props = Props(new ServerActor(id: Int, numNodes: Int))
  sealed trait Command

  private val conf = ConfigFactory.load("application.conf")

  case class initializeFingerTable(nodeIndex: Int, activeNodes: mutable.TreeSet[Int])
  case class initializeFirstFingerTable(nodeIndex: Int)
  case class getDataServer(nodeIndex: Int, hash:Int)
  case class loadDataServer(data: EntityDefinition, nodeIndex: Int, hash: Int)
  case class updateOthers(activeNodes: mutable.TreeSet[Int])
  case class updateTable(s: Int, i: Int)
  case class removeNodeServer(activeNodes: mutable.TreeSet[Int])

  case class Envelope(nodeIndex : Int, command: Command) extends Serializable

  val entityIdExtractor: ExtractEntityId ={
    case Envelope(nodeIndex, command) => (nodeIndex.toString,command)
  }

  val num_of_shards = conf.getInt("num_shards")
  val shardIdExtractor: ExtractShardId ={
    case Envelope(nodeIndex, _) => Math.abs(nodeIndex.toString.hashCode % num_of_shards).toString
  }


  def startMerchantSharding(system: ActorSystem, id: Int, numNodes : Int): ActorRef = {
    ClusterSharding(system).start(
      typeName = "Server",
      entityProps = ServerActor.props(id, numNodes),
      settings = ClusterShardingSettings(system),
      extractEntityId = entityIdExtractor,
      extractShardId = shardIdExtractor
    )
  }
}
