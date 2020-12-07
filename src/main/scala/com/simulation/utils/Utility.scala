package com.simulation.utils

import com.simulation.beans.EntityDefinition

import scala.collection.mutable.ListBuffer
import scala.io.Source

object Utility {
  def md5(s: String, bitsNumber: Int): Int = {
    val md = java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
    val bin = BigInt(md, 16).toString(2).take(bitsNumber)
    Integer.parseInt(bin, 2)
  }


  def getMoviesData: ListBuffer[EntityDefinition] = {
    val dataList: ListBuffer[EntityDefinition] = new ListBuffer[EntityDefinition]
    val lines = Source.fromResource("data.csv")
    var i: Int = 0
    for (line <- lines.getLines.drop(1)) {
      val cols = line.split(",")
      dataList += EntityDefinition(i, cols(0))
      i += 1
    }
    dataList
  }
}
