package com.simulation

import com.datastax.driver.core.{Cluster, Session}
import com.simulation.ChordActorDriver.logger
import com.simulation.beans.EntityDefinition

object ConnectToCassandra {
  var cluster:Cluster = null
  var session:Session = null

  def setup(keyspaceName:String, host:String, port:Int) :(Cluster,Session) = {
    cluster = Cluster.builder().addContactPoint(host).withPort(port).build()
    session = cluster.connect()

    //Query to create a keyspace
    var query = "CREATE KEYSPACE IF NOT EXISTS "+keyspaceName+" WITH replication = {'class':'SimpleStrategy', 'replication_factor':3} ;"
    session.execute(query)

    //Connect to the testkeyspace keyspace
    session = cluster.connect(keyspaceName)


    (cluster,session)
  }

  def close() = {
    if( session != null ) session.close()
    if( cluster != null ) cluster.close()
  }

  def createTable(): Unit ={
    val keyspace = "nodes"
    val (cluster, session) = ConnectToCassandra.setup(keyspace, "localhost", 9042)

    //Create table
    val query=  "CREATE TABLE  IF NOT EXISTS movie(id int PRIMARY KEY, name text);"
    logger.info("Table created if it does not exist")
    session.execute(query)
  }

  def addToCassandra(Data: EntityDefinition) = {
    try {
      val keyspace = "nodes"
      val (cluster, session) = ConnectToCassandra.setup(keyspace, "localhost", 9042)

      //Insert data into the table
      var query = "INSERT INTO movie (id, name) VALUES("+Data.id+",'"+Data.name+"');"
      session.execute(query)

      //Retrieve data from movie table
      query = "SELECT * FROM movie;"
      val result = session.execute(query)
      logger.info("Column definition: " + result.getColumnDefinitions.toString)
      logger.info("Fetching the stored data from Cassandra: " + result.all)

    }
    finally {
      ConnectToCassandra.close()
    }
  }
}