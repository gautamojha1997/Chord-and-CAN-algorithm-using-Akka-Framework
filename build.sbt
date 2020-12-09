name := "group_6"

version := "0.1"

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(

  "org.scalatest" %% "scalatest" % "3.0.8" % "test",

  "com.typesafe" % "config" % "1.4.1",

  "junit" % "junit" % "4.13.1" % Test,

  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.10",

  "com.typesafe.akka" %% "akka-cluster-typed" % "2.6.10",

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.10" % Test,

  "com.typesafe.akka" %% "akka-remote" % "2.6.10",

  "com.typesafe.akka" %% "akka-http" % "10.2.1",

  "com.typesafe.akka" %% "akka-stream" % "2.6.10",

  "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.10",

  "com.typesafe.akka" %% "akka-http-testkit" % "10.2.1",

  "ch.qos.logback"    % "logback-classic" % "1.2.3",

  "org.ddahl" %% "rscala" % "3.2.19",

  "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % "2.0.2",

  "com.datastax.cassandra" % "cassandra-driver-core" % "3.10.2",

  "com.codahale.metrics" % "metrics-core" % "3.0.2",

  "com.codahale.metrics" % "metrics-core" % "3.0.2", "com.typesafe.akka" %% "akka-cluster-sharding-typed" % "2.6.10",
//  "com.datastax.oss" % "java-driver-query-builder" % "4.9.0",


)