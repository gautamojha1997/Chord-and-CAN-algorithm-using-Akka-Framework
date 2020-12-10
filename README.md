# CS 441 Project : Implementation of Chord and CAN algorithm using Akka Simulations.

## Overview
The Homework aimed to make simulations to demonstrate working of Cloud Simulator using Chord Algorithm upto add Node, the project aims to demonstrate simulations with continuation of Chord for fault tolerance and implementation of CAN algorithm ,for the implementation we are using Akka which is a
open source toolkit for designing scalable, resilient systems that span processor cores and networks. The Project uses Akka Cluster Sharding actor deployment model.

### Project Members

- Aishwarya Sahani
- Ansul Goenka
- Gautamkumar Ojha
- Mihir Kelkar

### Instructions to run the simulations
#### Prerequisites
- Install [Simple Build Toolkit (SBT)](https://www.scala-sbt.org/1.x/docs/index.html) 
- Akka Cluster Sharding [SBT dependency](https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html)
- Install [Cassandra](https://cassandra.apache.org/download/)
- To install Cassandra you will need Python 2.7 & Java SE Development Kit 8u251. Follow [link](https://phoenixnap.com/kb/install-cassandra-on-windows).
- After installation, to enable Cassandra, set the value of the flag *enableCassandra* to *true* in the application.conf file.

#### Run 
1. Clone the project - ```git clone https://ojhagautam97@bitbucket.org/cs441-fall2020/group_6.git```
2. Navigate to the project folder - cd group_6/
3. Run the simulations with the command : ```sbt clean compile run```
4. Run the test cases using the command : ```sbt clean compile test```
5. Open the link generated by the WebService at ```http://localhost:8080/```

### Docker
- The docker image of the repository has been uploaded to dockerhub
- In order to pull the image to your system, use the following command - ``` docker pull mkelka3/course_project:1.1 ```
- In order to get the image id, use the following command - ```docker images```
- Now, to run the image - ```docker run -t --network host <imageid>```

### Modules
- #### [AKKA](https://doc.akka.io/docs/akka/current/index.html)
Akka is a free and open-source toolkit and runtime simplifying the construction of concurrent and distributed applications on the JVM. Akka supports multiple programming models for concurrency, but it emphasizes actor-based concurrency. We have created 4 akka actors, user actor reads data from the dataset and sends it to the supervisor actor which manages several server actors, these server actors are used to simulate chord and finger actors are used to realize the finger table.
- #### [Monte Carlo](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2924739/)
Monte Carlo simulations are used to model the probability of different outcomes in a process that cannot easily be predicted due to the intervention of random variables. It is used to predict the probability of different outcomes when the intervention of random variables is present. Monte Carlo introduces randomness into our simulation model by randomnly selecting various the operations and values on specifying the total number of operations. Monte Carlo simulation is supported by the integration of the R statistical package in this project.
The simulation is done by generating random requests to the API. In order to introduce randomness, the eval function of the R client is used. 
- #### [Cassandra](https://cassandra.apache.org/)
Apache Cassandra is a free and open-source, distributed, wide column store, NoSQL database management system designed to handle large amounts of data across many commodity servers, providing high availability with no single point of failure. Cassandra offers robust support for clusters spanning multiple datacenters, with asynchronous masterless replication allowing low latency operations for all clients. We have used Cassandra to save the state of server actors, the movie id and movie name has been stored & fetched after the server actor loads the data.
- #### [Akka Cluster Sharding](https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html)
Cluster Sharding is an actor deployment model which is useful when it is needed to distribute actors across several nodes in the cluster. Logical identifier of the actors are used to be able to interact with them without being concerned about their physical location in the cluster.

### Coding Structure

- Utility
    - This object file takes a string and number of bits to return hashed value used for generating keys inserted into DHT and for data units.
    - The hashing algorithm used is MD5.
- Data.csv
    - Represents the movie data in id & name format.
- WebService 
    - This is the entry point for our project, this scala file uses Akka-HTTP library for making routes for different options to run the simulations 
    by calling methods for required simulation task.
    - After running this object file you get a link ```http://localhost:8080/``` which will redirect to the webpage with 2 options: 
    
### 1.Chord
- After clicking on chord you will redirected to the webpage with 6 buttons:
    - Add Node : Clicking this button calls method createServerNode() which adds node.
    - Load Data : Clicking this button calls method loadData(id.toInt) which loads the result in the form of string in the server. To load data append ?id=<any integer> to your link.
    - Lookup Data : Clicking this button calls method getData(id.toInt) which is used by the user to look data over a server node using Chord Protocol. To look-up data append ?id=<any integer> to your link.
    - Snapshot : Clicking this button simply returns all the result for the simulation.
    - Remove Node : Clicking this button calls method removeNode() which remove node.
    - Montecarlo : Clicking this button invokes the Rclient object to randomly select the 4 options from above. The 4 options are generated randomly and they are : 1.AddNode, 2.Snapshot, 3.LoadData, 4.LookupData. To use Monte-Carlo append ?number=<any integer> to your link.
        
- ChordActorDriver
    - This object file defines the number of users, servers, ActorSystem, Actors (serverActor, userActor, supervisorActor, fingerActor).
    - It also defines methods used by Webservices and defined in the actor class files to load data, lookup data and display the result.

-  ServerActor
    - This class file represents actor-server which implements chord algorithm and defines messages as follows:
        - case class initializeFirstFingerTable(nodeIndex: Int) : It initializes the first finger table for the first server-node.
        - case class initializeFingerTable(nodeIndex: Int, activeNodes: mutable.TreeSet[Int]) : It initializes the finger table for all the server nodes after the first one.
        - case class updateOthers(activeNodes: mutable.TreeSet[Int]) : It updates all nodes whose finger table should refer to the new node which joins the network.
        - case class updateTable(s: Int, i: Int) : It is invoked by updateOthers which recursively updates finger-table.
        - case class getDataServer(nodeIndex: Int, hash:Int) : Returns output when looked-up is performed. 
        - case class loadDataServer(data: EntityDefinition, nodeIndex: Int, hash: Int) : Loads the data to the server by calling method loadData.
        - case class removeNodeServer(activeNodes: mutable.TreeSet[Int]) : It handles fault tolerance of chord by removing a particular node, updating finger table of other nodes and transfer data to proper other node after the removal of that particular node.
        - case class Envelope(nodeIndex : Int, command: Command) extends Serializable : A serializable class which is used to extract id for the entity actor and id for the shard that entity actor belongs to. The Entity Id is extracted by simply taking string value of the node index and for shard id it is extracted by hashing entity id with modulo number of shards (Math.abs(nodeIndex.toString.hashCode % num_of_shards).toString).
    - Also, the class defines following methods :
        - getData(id: Int, m: Int, hash: Int) : Returns result in the form of string when invoked by getDataServer(id: Int, m: Int, hash: Int).
        - findSuccessor(nodeIndex: Int, activeNodesList:List[Int], entry:Int) : Returns successor value for the given node by fetching successor value for an arbitrary node and eventually updating the successor value for the given node.
        - extendDHT(activeNodes: mutable.TreeSet[Int]) : This function transfer data from deleted node to a new suitable node.
        - belongs(s:Int, n: Int, successorValue: Int) : Invoked by updateTable() to check whether the node belongs within the range of predecessor and fingerIthEntry value.
        - def loadData(data: EntityDefinition, nodeIndex: Int, hash: Int) : Returns result in the form of string when invoked by loadDataServer(data: EntityDefinition, nodeIndex: Int, hash: Int).
        - def startMerchantSharding(system: ActorSystem, id: Int, numNodes : Int): Returns reference of the sharded server actor when called by the driver.
        
- UserActor
    - This class file represents actor-user and defines messages as follows:
        - case class loadData(data:EntityDefinition) : Returns result of the loaded data from the server to the user. 
        - case class getDataUserActor(id:Int) : Returns result by looking up data from the server.
        - case class createUserActor(id:Int) : Returns path of created user actor.
        
- SupervisorActor 
    - This class acts as a bridge between user and the server actor. The user actor invokes the messages defined in this class which returns results by invoking messages defined in the ServerActor.
    
- FingerActor
    - This actor is used to simulate the fingerTable.
	- case class fetchFingerTable(nodeIndex: Int): It is used to fetch finger Table of a given node
	- case class updateFingerTable(finger: mutable.LinkedHashMap[Int, Int], nodeIndex: Int): This is used to update finger Table of a given node
	- case class getFingerValue(nodeIndex: Int, index: Int): It is used to get the successor value of a given row for a node
	- case class setFingerValue(nodeIndex: Int, index: Int, value:Int): It is used to set the successor value of a given row for a node
	- case class getSuccessor(nodeIndex: Int): It is used to fetch the successor of a given node
	- case class getPredecessor(nodeIndex: Int): It is used to fetch the predecessor of a given node
	- case class setSuccessor(nodeIndex: Int, value: Int): It is used to set the successor of a given node
	- case class setPredecessor(nodeIndex: Int, value: Int): It is used to set the predecessor of a given node
	- case class fetchData(nodeIndex: Int, key: Int): It is used to fetch the data stored at the node
	- case class storeData(nodeIndex: Int, dht: EntityDefinition): It is used to store the data stored at that node
	- case class extendData(nodeIndex: Int, dht: mutable.HashMap[Int, String]) : It is used to add the data of deleted node to a node with index "nodeIndex"
	- case class containsData(nodeIndex: Int): This is used to check if a given node has any data stored in it.
    
### 2.CAN
- After clicking on CAN you will redirected to the webpage with 6 buttons:
  - Add Node : Clicking this button calls method createServerNodeCAN() which adds node.
  - Load Data : Clicking this button calls method loadData(id.toInt) which loads the result in the form of string in the server. To load data append ?id=<any integer> to your link.
  - Lookup Data : Clicking this button calls method getData(id.toInt) which is used by the user to look data over a server node using Chord Protocol. To look-up data append ?id=<any integer> to your link.
  - Snapshot : Clicking this button simply returns all the result for the simulation.
  - Remove Node : Clicking this button calls method removeNode() which remove node.
  - Montecarlo : Clicking this button invokes the Rclient object to randomly select the 4 options from above. The 4 options are generated randomly and they are : 1.AddNode, 2.Snapshot, 3.LoadData, 4.LookupData. To use Monte-Carlo append ?number=<any integer> to your link.

- CANActorDriver
    - This object file defines the number of active servers, ActorSystem, Actors (nodeActor, bootstrapActor).
    - It also defines methods used by Webservices and defined in the actor class files to load data, lookup data and display the result.
    
- BootstrapActor
    - Class which handles the bootstrap server actor which keeps track of all CAN nodes and defines messages as follows:
        - case class createServerActorCAN(serverCount: Int) : Creates a new node actor & adds a node & updates the neighbours
        - case class getDataBootstrapCAN(id: Int) : Fetches the data using id
        - case class loadDataBootstrapCAN(data: EntityDefinition) : Loads the data id & value in any node
        - case class getSnapshotCAN() : Gets the system state by fetching all nodes & their neighbours
        - case class removeBootstrapNode(nodeIndex:Int) : Removes an added node & updates its neighbours 
    - Also, the class defines following methods :
        - findNeighbours(server: Int, nodeActor:ActorRef): Fetches all the neighbours of current node
        - belongs(x1: Double, x2: Double, x3: Double, x4: Double, y1: Double, y2: Double, y3: Double, y4: Double): Method checks if 2 nodes are neighbours using cartesian coordinates
        - findXneighbours(node: Coordinates, nodeActor: ActorRef): Finds all the X axis neighbours
        - findYneighbours(node: Coordinates, nodeActor: ActorRef): Finds all the Y axis neighbours
        - updateNeighbours(nodeIndex : Int): Updates neighbours when node a node is split to check if neighbours have changed
        - searchNode(start:Int, id:Int): Hops thru neighbours using dfs to locate the node where id exists
        - updateCoordinates(node: Coordinates) : Update node coordinate changes to consequent nodes
- NodeActor
    - Class to handle the individual node, manage its neighbours & data and defines messages as follows:
        -  case class fetchDHT() : Returns all the keys & values that the node has saved
        -  case class loadDataNode(data: EntityDefinition) : load data into that node
        -  case class addNeighbour(coordinates: Coordinates) : Add a new neighbour to the node
        -  case class getNeighbours() : Get all the existing neighbours of node
        -  case class removeNeighbour(server: Int) : Remove an existing neighbour from the node
        -  case class updateCoordinatesNode(coordinates: Coordinates) : Updates the coordinates of the neighbours 
        -  case class transferDHT(dhtTransfer: mutable.HashMap[Int, String]) : Appends the hashmaps of 2 nodes - current & transfered
        - case class Envelope(nodeIndex : Int, command: Command) extends Serializable : A serializable class which is used to extract id for the entity actor and id for the shard that entity actor belongs to. The Entity Id is extracted by simply taking string value of the node index and for shard id it is extracted by hashing entity id with modulo number of shards (Math.abs(nodeIndex.toString.hashCode % num_of_shards).toString).

## Results

### 1. Chord

1.Adding Node : Adding the created node.

- First Node Added : 11

```
INFO  [SupervisorActor]: Sever Actor Created: 11
INFO  [ServerActor]: LinkedHashMap(12 -> 11, 13 -> 11, 15 -> 11, 3 -> 11)
```

- Second Node Added : 12

```
INFO  [SupervisorActor]: Sever Actor Created: 12
INFO  [ServerActor]: Checking Values of FingerTableList((12,11), (13,11), (15,11), (3,11))
INFO  [ServerActor]: Checking Values of FingerTableList((13,12), (14,12), (0,12), (4,11))
```

2.Load Data : Using id=7 to load data at any server node (The id has to be passed at the end of the url as follows: ?id=7)


```
INFO  [WebService$]: In loadData webservice
INFO  [WebService$]: In loadData webservice
INFO  [ChordActorDriver$]: In loadData driver
INFO  [UserActor]: Loading data using user actor
INFO  [SupervisorActor]: In loadDataSupervisor SupevisorActor
INFO  [ServerActor]: Checking if 4 belongs in the range 12 - 14
INFO  [ServerActor]: Checking if 4 belongs in the range 13 - 16
INFO  [ServerActor]: Checking if 4 belongs in the range 15 - 4
INFO  [ServerActor]: Checking if 4 belongs in the range 3 - 13
INFO  [ServerActor]: Data stored at 12
```
- WebService result
    - Loaded Data at 12 : ```Added: Id: 7, Name: Waiting For Forever```

3.Lookup Data : Looking up data with id = 7 to check whether the data loaded at 6 can be retrieved. (The id has to be passed at the end of the url as follows: ?id=7)

```
INFO  [ServerActor]: Data stored at 12
INFO  [UserActor]: Fetching data from server actor
INFO  [ServerActor]: Checking if 4 belongs in the range 12 - 14
INFO  [ServerActor]: Checking if 4 belongs in the range 13 - 16
INFO  [ServerActor]: Checking if 4 belongs in the range 15 - 4
INFO  [ServerActor]: Checking if 4 belongs in the range 3 - 13
INFO  [ServerActor]: Data was stored at 12 
```

- WebService result
    - Looking Up data at 12 : ```Lookup value: 7 Some(Waiting For Forever)```

4.Snapshot : Returns the overall Fingertable value.

```
INFO  [WebService$]: Snapshot Web Service
INFO  [ChordActorDriver$]: Print Snapshot Driver
INFO  [SupervisorActor]: Get Snapshot
INFO  [SupervisorActor]: LinkedHashMap(12 -> 12, 13 -> 11, 15 -> 11, 3 -> 12)
INFO  [SupervisorActor]: Get Snapshot
INFO  [SupervisorActor]: LinkedHashMap(13 -> 12, 14 -> 12, 0 -> 12, 4 -> 12)
```

- Webservice result
    - Snapshot created : ```11 -> LinkedHashMap(12 -> 12, 13 -> 11, 15 -> 11, 3 -> 12) 12 -> LinkedHashMap(13 -> 12, 14 -> 12, 0 -> 12, 4 -> 12)```

5.Remove Node : Removing the node using id of the node created. Here we will remove node 12 as id=12 and transfer data to a new node as node 12 contains data.

- Node Removed : 12
```
INFO  [WebService$]: In removeNode webservice
INFO  [ServerActor]: Removing node with index = 12
INFO  [ServerActor]: Checking if 4 belongs in the range 12 - 14
INFO  [ServerActor]: Checking if 4 belongs in the range 13 - 16
INFO  [ServerActor]: Checking if 4 belongs in the range 15 - 4
INFO  [ServerActor]: Checking if 4 belongs in the range 3 - 13
INFO  [ServerActor]: Data stored at 11
```

Snapshot after node 12 is removed : ```11 -> LinkedHashMap(12 -> 11, 13 -> 11, 15 -> 11, 3 -> 11)```
 
    
6.MonteCarlo : Generates random requests based on the number specified. In order to introduce randomness, the eval function of the R client is used.

Here we have used numbers = 5.

- First choice is 1.AddNode, thus a server actor is created at 0 as below:
```
INFO  [WebService$]: choice = 1
INFO  [Slf4jLogger]: Slf4jLogger started
INFO  [SupervisorActor]: Sever Actor Created: 0
INFO  [ServerActor]: LinkedHashMap(1 -> 0, 2 -> 0, 4 -> 0, 8 -> 0)
```

- Second choice is 3.LoadData with id=49 is loaded as below and stored at 0:
```
INFO  [WebService$]: choice = 3
INFO  [ActorDriver$]: In loadData driver
INFO  [UserActor]: In loadData UserActor
INFO  [SupervisorActor]: In loadDataSupervisor SupevisorActor
INFO  [ServerActor]: Checking if 7 belongs in the range 1 - 3
INFO  [ServerActor]: Checking if 7 belongs in the range 2 - 5
INFO  [ServerActor]: Checking if 7 belongs in the range 4 - 9
INFO  [ServerActor]: Data stored at 0
```

- Third choice is 1.AddNode, thus a server actor is created at 10 as belows:
```
INFO  [WebService$]: choice = 1
INFO  [SupervisorActor]: Sever Actor Created: 10
```

- Fourth choice is 3.LoadData with id = 34 is loaded as below and stored at 0:
```
INFO  [WebService$]: choice = 3
INFO  [ActorDriver$]: In loadData driver
INFO  [UserActor]: In loadData UserActor
INFO  [ServerActor]: Data stored at 0
```

- Fifth choice is 1.AddNode, thus a server actor is created at 5 as belows:
```
INFO  [WebService$]: choice = 1
INFO  [ServerActor]: Successor Found, value = 0
INFO  [ServerActor]: Second Instance: LinkedHashMap(11 -> 0, 12 -> 0, 14 -> 0, 2 -> 0)
INFO  [SupervisorActor]: Sever Actor Created: 5
```

- Finally this is how the overall result is for numbers=5
```
INFO  [WebService$]: 1.AddNode: NodeAdded
3.LoadData(49): Id: 49, Name: Knocked Up
1.AddNode: NodeAdded
3.LoadData(34): Id: 34, Name: New Year's Eve
1.AddNode: NodeAdded
```

- Webservice result
    - MonteCarlo result : ```1.AddNode: NodeAdded 3.LoadData(49): Id: 49, Name: Knocked Up 1.AddNode: NodeAdded 3.LoadData(34): Id: 34, Name: New Year's Eve 1.AddNode: NodeAdded```
    

7.Cassandra logs 
```
  INFO  [Cluster]: New Cassandra host localhost/127.0.0.1:9042 added
  INFO  [ActorDriver$]: Table created if it does not exist
  INFO  [ClockFactory]: Using java.lang.System clock to generate timestamps.
  INFO  [DCAwareRoundRobinPolicy]: Using data-center name 'datacenter1' for DCAwareRoundRobinPolicy (if this is incorrect, please provide the correct datacenter name with DCAwareRoundRobinPolicy constructor)
  INFO  [Cluster]: New Cassandra host localhost/127.0.0.1:9042 added
  INFO  [ActorDriver$]: Column definition: Columns[id(int), name(varchar)]
  INFO  [ActorDriver$]: Fetching the stored data from Cassandra: [Row[43, Love Happens], Row[4, Water For Elephants], Row[7, Waiting For Forever], Row[57, Good Luck Chuck], Row[56, Dear John]]
```


### 2. CAN 

1.Adding a node : Add the created node.

- Adding first node:

```
INFO  [CANActorDriver$]: Add Node Driver
INFO  [BootstrapActor]: Node being added => 0
INFO  [BootstrapActor]: Active nodesHashMap(0 -> Coordinates(0,0.0,1.0,0.0,1.0))
```

- Adding Second node:

```
INFO  [CANActorDriver$]: Add Node Driver
INFO  [BootstrapActor]: Node being added => 1
INFO  [BootstrapActor]: Node being split => 0
INFO  [BootstrapActor]: Neighbour of server Coordinates(1,0.0,1.0,0.5,1.0) -> Coordinates(0,0.0,1.0,0.0,0.5)
INFO  [BootstrapActor]: Updating coordinates of node => Coordinates(0,0.0,1.0,0.0,0.5)
INFO  [BootstrapActor]: Active nodesHashMap(0 -> Coordinates(0,0.0,1.0,0.0,0.5), 1 -> Coordinates(1,0.0,1.0,0.5,1.0))
```

2.Load Data : Using id=3 to load data at the created server above(The id has to be passed at the end of the url as follows: ?id=3)

```
INFO  [WebService$]: In loadData webservice
INFO  [WebService$]: In loadData webservice
INFO  [CANActorDriver$]: Load data Driver
INFO  [BootstrapActor]: Node where to load data => 0
```

3.Lookup Data : Looking up data with id=3 to check whether the data is loaded at the created node 0.
```
INFO  [CANActorDriver$]: Get data Driver
INFO  [BootstrapActor]: Get row => 3
INFO  [BootstrapActor]: Searching id = 3 -> Starting from node =0
INFO  [BootstrapActor]: Found in node = 0
```

- Webservice result : ```Lookup value: What Happens in Vegas```

4.Snapshot : Returns the overall Co-ordinate and map table.

```
INFO  [WebService$]: Snapshot Web Service
INFO  [CANActorDriver$]: Print Snapshot Driver
INFO  [BootstrapActor]: HashMap(1 -> Coordinates(1,0.0,1.0,0.5,1.0))
INFO  [BootstrapActor]: HashMap(0 -> Coordinates(0,0.0,1.0,0.0,0.5))
```

- Webservice result: 
```
Snapshot created
0 Coordinates(0,0.0,1.0,0.0,0.5) -> HashMap(1 -> Coordinates(1,0.0,1.0,0.5,1.0))
1 Coordinates(1,0.0,1.0,0.5,1.0) -> HashMap(0 -> Coordinates(0,0.0,1.0,0.0,0.5))
```

5.Remove Node : remove a node with id=0

```
INFO  [WebService$]: In removeNode webservice
INFO  [WebService$]: In removeNode webservice
INFO  [CANActorDriver$]: Remove node Driver
INFO  [BootstrapActor]: Keys moved to 1
INFO  [BootstrapActor]: Node removed
```

- Webservice result - ```Node removed: 0```

6.Monte Carlo : Used the similar approach to chord, and the results are as follows:

- Used number = 5 for CAN monteCarlo.

```
INFO  [WebService$]: choice = 4
INFO  [WebService$]: choice = 4
INFO  [WebService$]: choice = 2
INFO  [WebService$]: choice = 2
INFO  [WebService$]: choice = 1
INFO  [CANActorDriver$]: Add Node Driver
INFO  [WebService$]: 4.LookupDataCreate a node first
4.LookupDataCreate a node first
2.Snapshot: Create a node first
2.Snapshot: Create a node first
1.AddNode: NodeAdded
```

- Webservice result - ```4.LookupDataCreate a node first 4.LookupDataCreate a node first 2.Snapshot: Create a node first 2.Snapshot: Create a node first 1.AddNode: NodeAdded```



## AWS EC2 Deployment
We have given the link to make your ec2 instance and how to connect to it and also how to install docker in ec2.

- AWS [cli install](https://docs.aws.amazon.com/cli/latest/userguide/install-linux.html#install-linux-pip).

- Firstly to start with AWS EC2 deployment one needs to create an [ec2 instance](https://docs.aws.amazon.com/quickstarts/latest/vmlaunch/step-1-launch-instance.html).
- Secondly, after creating you need to connect to your instance
- Thirdly, you need to install a docker in your [ec2 instance](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/docker-basics.html).
- Lastly, you can view how we have deployed our project on ec2 after completing above steps!! [EC2 deployment](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/docker-basics.html).




        
        
        




