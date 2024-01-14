
## Configurations of the Model

To develop our simulation we utilized the Peersim simulation engine, which allows for large-scale simulations with high dynamicity to be run. Furthermore, the Peersim Engine allows for customizable and in-depth configuration of the simulation through configuration files. A feature we find very useful.

In the subsequent section, we will delve into a comprehensive explanation of the simulation configurations.



### Configuration of the Simulation
Before beginning to explain the possible configurations of the different parts of the network we need to go over what these parts are. The simulation works by first generating a set of nodes that can be of two different types, Workers and Clients. The Clients act as task generators of work and collect metrics on the conclusion of tasks. Whereas the Worker Node acts as a processing node for said tasks, meaning the Worker node is the one doing the actual computing of the tasks. The Worker nodes also may host a controller component, which is a protocol that will run on the same node of the workers and is responsible for collecting information about the node's neighborhood and sending information about the node's neighborhood in one-hop broadcasts.

The challenge of task offloading encompasses various configurations, and we have implemented some of these specific instances. These include Online-Binary task offloading, where the decision to offload the next available task is made dynamically; and Batch Task offloading, where, at each time step, decisions are made for every task that arrived in the last time-step regarding offloading. Furthermore, we are working on implementing a dependency-cognisant task offloading simulation.
The selection of these modes is done by defining a base file that outlines the appropriate protocols for each instance of the problem. Beyond determining the simulation mode, the configurability extends to a range of global parameters applicable to all instances, in the remainder of this section we will be going over the different configurations that are allowed.

The global configurations encompass six domains: Global Configurations, Worker-specific configurations, Controller-specific configurations, Client-specific configurations, Cloud-specific configurations, and lastly, topology and network-specific configurations.


### Global Configurations
#### Overall configurations of the simulaiton
- **Size of the Network.** Defines the number of nodes in the simulation, due to the properties of Peersim, if we have stipulated size N to the simulation then there are N workers and N Clients.
  ```
  SIZE 10
  ...
  network.size SIZE
  ```

- **Number of times simulation is executed.** The constant 'CYCLES' defines the total number of complete cycles (ticks in the simulation) that a simulation starting from 0 takes to end. We should note the actual variable that sets this value is 'simulation.endtime', but for convenience

  The constant 'CYCLE' is used to define the number of ticks to reschedule the making of an offloading decision. For example, if the value is left at one an offloading decision will be made every time step.
  ```
  CYCLES 1000
  CYCLE 1
  
  # You only need to set CYCLE and CYCLES, not recomended to alter the settings 
  # bellow directly. They are shown for informative purposes only.
  ...
  simulation.endtime CYCLE*CYCLES
  ```
- **Bounds of the delay a message can have.** Setting MINDELAY and MAXDELAY will allow messages to randomly be delayed when being delivered.
  The delay will be such that MINDELAY <= delay <= MAXDELAY and MAXDELAY == MINDELAY == 0 means no delay.
  ```
  MINDELAY 0
  MAXDELAY 0
  ```

- **The probability of a package/message being lost.** Allows messages to be lost.
  ```
  DROP 0
  ```

- **Define the indexes of the nodes that have the controller function.** This parameter specifies the indexes of the nodes on the global network vector that have a controller function.
  ```
  CONTROLLERS 0,1,2
  ```

- **The flag that manages the existence of the Cloud** This flag can take values of either 1 or 0. If the flag is set to 1 an extra node not included in the nodes specified from 'SIZE' is created. This means that if 'SIZE 10' and 'CLUD_EXISTS 1' then there is a total of 11 nodes.
    ```
    CLOUD_EXISTS 1
    ```


#### Configurations of the DiscreteTimeStepManager and PettingZoo environment
- **Utility Reward..** a parameter of the reward function acts as a weight in the expression that computes the utility of a reward. This parameter receives an int. It is directly used by the environment and not the simulation.
    ```
    protocol.mng.r_u 1
    ```

- **Delay Weight.** A parameter of the reward function acts as a weight in the expression that computes the cost associated with the delay induced by taking an action. This parameter receives an int. It is directly used by the environment and not the simulation.
    ``` 
    protocol.mng.X_d 1
    ```

- **Overload Weight..** a parameter of the reward function acts as a weight in the expression that computes the cost associated with node overloading (overloading of a node happens when the node has too many tasks assigned and starts losing tasks) induced by taking an action.
  This parameter receives an int. It is directly used by the environment and not the simulation.
    ``` 
    protocol.mng.X_o 150
    ```

### Configurations of the Client
All the work generated on the simulation is in the form of applications, which in turn consist of groups of tasks. The Clients generate applications, and the types of applications generated have different properties depending on the type of simulation being implemented. In the case of Binary-Online and batch offloading an application consists of a single task. In a simulation with dependencies, an application consists of a Directed Acyclic Graph(DAG) of tasks.

A task consists of an amount of instructions to be processed, a total data size inputted, and a cost in CPU cycles per instruction.

Similarly to the tasks, there can also be multiple types of DAGs, in the simulations with dependencies the DAG type is selected randomly whenever the client is generating an application. A DAG is modeled as a set of tasks, in which the type is randomly selected on creation, a list of dependencies that must always start with task 0 and end in the last task.

For each of the DAG or task types, all the configurations must be specified, otherwise an error is thrown on environment creation. In the case of the dependency-less simulations, there must be only one DAG type with one vertice and no edges.

#### Client's parameters

- **Task Arrival Rate per Client**. This parameter acts as the event rate of an exponential distribution, that rules when the next application to be sent to a given node will be generated. Each client keeps track of when to send a new application to each of the workers it can see independently. The computation of the time for the next event is done by inverting the cumulative distribution function of the exponential distribution and sampling a uniform distribution between 0 and 1, by applying the inversed distribution on the sampled value we obtain the time for the next event corresponding to the sampled value.
    ```
    protocol.clt.taskArrivalRate 0.1
    ```

#### Task Parameters

- **Max Possible deadline.** this parameter allows for defining the minimum deadline. To disable deadlines set this parameter to a be less or equal to zero.
    ``` 
    protocol.clt.minDeadline 100
    ```

- **Number of Tasks.** this parameter specifies the total number of task types in the simulation.
    ``` 
    protocol.clt.numberOfTasks 2
    ```

- **Ratios of each task type.** the ratio at which each task type is selected is specified through this parameter. The ratios must be separated by a ',' without any spaces. The actual values used do not matter as the weights will be scaled and converted into probabilities.
  ```
  protocol.clt.weight 4,6
  ```

- **Average Number of Cycles in an Instruction.** the number of cycles per instruction of each task type must be separated by a ',' without any spaces. This parameter is used in two ways:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It is considered in computing the time it takes for a simulation to finish a task.
    ```
    protocol.clt.CPI 1,1
    ```

- **Byte Size of Task.** the byte sizes of a task of each task type must be separated by a ',' without any spaces. This parameter is measured in Mbytes and used in computing the cost in time of communication when offloading tasks and impacts the communication cost in the Reward function.
    ``` 
    protocol.clt.T 150,100
    ```

- **Number of Instructions per Task.** the byte sizes of a task of each task type must be separated by a ',' without any spaces. This parameter, similarly to the CPI, is used in two ways:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It is considered in computing the time it takes for a simulation to finish a task.
  ``` 
  protocol.clt.I 200e6,250e6
  ```

  A simulation started on the definitions of the examples would have two task types:
  ```
  1st type: {CPI:1, T:150, I:200e6} -> A task will be generated with type 1 40% of the time (4/(4+6))
  2nd type: {CPI:1, T:100, I:250e6} -> A task will be generated with type 2 60% of the time (6/(4+6))
  ```

#### DAG Parameters
- **Number of DAGs.** this parameter specifies the total number of DAG types in the simulation.
    ``` 
    protocol.clt.numberOfDAG 2
    ```

- **Ratios of each DAG type.** the ratio of each DAG type must be separated by a ',' without any spaces.
  The ratios must be separated by a ',' without any spaces. The actual values used do not matter as the weights will be scaled and converted into probabilities.
  ```
  protocol.clt.dagWeights 4,6
  ```

- **Edges.** this parameter specifies the edge configurations of each of the DAG types.
  an edge is represented by a string of the form predecessorVertice->successorVertice; different edges within a DAG are separated by a ','. The edge sets of different DAGs are separated by a ';'. Furthermore, the edges of a DAG must obey the following rules:
    - Task 0, the initial task, must be a predecessor to all tasks and have no predecessors of its own.
    - If there are n+1 vertices the vertice of index n, must be a successor to all tasks and have no successors of its own.

  ```
  protocol.clt.edges 0->1,1->2,2->3,3->4,4->5,5->6,6->7,0->8,8->7,7->9
  ```

- **The number of vertices in the DAG.** This parameter indicates the total amount of vertices in the DAG. This value must be the value of the highest indexed vertice in the edges plus one.
  ``` 
  protocol.clt.vertices 10
  ```

A simulation created with the specifications above would have two DAG types
```
1. Vertices: {0,1,2,3,4,5}, noVertices 6
     Edges: {(0,1),(1,2),(1,3),(2,4)(3,4)(4,5)}

       /2\
    0-1   4-5
       \3/

2. Vertices: {0,1,2,3,4,5,6,7} noVertices = 8
     Edges: {(0,1),(1,2),(1,3),(1,7),(2,4)(3,4),(7,5),(4,5)}

       /2\
    0-1   4-5-6
       \\3//
        \7/
  ```



### Configurations of the Worker

The workers are defined in sets of nodes with the same properties, which we call layers. The layers are organized in a vector and for each layer there is a processing power, that consists of the number of available cores multiplied by the frequency of the worker's CPU, and a maximum queue size. We can add heterogeneity to the nodes in a layer by specifying a deviation term to the frequency of each CPU in that layer.

Nodes can only communicate with nodes on its layer, the layer immediately below and the one immediately above, for example, a node in layer index 1, would only be able to communicate with nodes in the layer index 0 and index 2. This can be overridden by manually specifying the links of the network, we shall explain how to do this later. Only nodes in layer 0 can communicate directly with the clients.

- **Specify the number of layers in the simulation,**  This flag informs the simulation of the total number of layers to be created. The value of this flag must be equal to the number of entries in the 'NO_NODE_PER_LAYER'.
    ```
    NO_LAYERS 2
    ```

- **Number of nodes in each layer,** this flag defines the number of nodes in each of the layers of the simulation. The sum of the nodes in all the layers must total the value in SIZE. Each entry is separated by a ',' and indicates the number of nodes to be put on the layer of index equal to its position, for example, if we have the configuration '5,0' then we would have 5 nodes in the layer of index 0 and 1 node in the layer of index 1.
    ```
    NO_NODES_PER_LAYERS 5,1
    ```

- **Number of Cores in Worker CPU**. This parameter specifies the number of cores a node in each layer will have, the value for each layer is separated by a ','. For example, given the value '4,8', the nodes in the layer index 0 will have 4 cores and the nodes in the layer index 1 will have 8 cores. The parameter is used for two things:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It is considered in computing the time it takes for a simulation to finish a task.
    ``` 
    NO_CORES 4,8
    ```

- **Frequency of Worker CPU**. This parameter is measured in instructions/second, and specifies the base frequency a node in each layer will have, the value for each layer is separated by a ','. For example, given the value '1e7,3e7', the nodes in the layer index 0 will have a frequency of 1e7 instr/second and the nodes in the layer index 1 will have a frequency of 3e7 instr/second. The parameter is used for two things:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It is considered in computing the time it takes for a simulation to finish a task.
    ``` 
    FREQS 1e7,3e7
    ```
- **Variations between frequencies of nodes in the same layer** This parameter specifies the variation of the frequency between nodes in each layer. This means that a node that is created in a layer with frequency 1e7 and variation 1e3 can be created with a frequency between [1e7-1e3, 1e7+1e3]
    ```
    VARIATIONS 1e3,1e3
    ```
- **Maximum Queue size**. This parameter is used multiple times when computing the reward function and is used as the threshold for a node to overload and start dropping tasks. Similarly to the frequency and number of cores, it is specified for each layer as a list of maximum queue lengths separated by ','.
    ``` 
    Q_MAX 10,50
    ```


### Configuring the Topology
There are three ways of configuring the topology of the network. The first is the manual way, where we can specify a concrete topology by indicating the position of the nodes and their links, alternatively. The second is the automatic way, where we randomly place the nodes and they will be able to communicate with everyone in a neighborhood of user-defined radius. Lastly, it is possible to specify the position of the nodes and have them linked to every node in a neighborhood of a user-specified radius.

- **Randomize Positions Flag,** this flag is the one that specifies whether the nodes are to be placed randomly or a topology was specified manually.
  ```
  RANDOMIZEPOSITIONS true
  ```

- **Specify the Positions of the nodes,** the coordinates of the nodes are specified in one String with the format "X0,Y0;X1,Y1;...". The coordinates of each node are separated by a ';', so the coordinates of the first node are (x0, Y0) and the coordinates of the second are (X1, Y1).
  ```
  init.Net0.POSITIONS 18.55895350495783,17.02475796027715;47.56499372388999,57.28732691557995;5.366872150976409,43.28729893321355;17.488160666668694,29.422819514162434;81.56549175388358,53.14564532018814;85.15660881172089,74.47408014762478;18.438454887921974,44.310130148722195;72.04311826903107,62.06952644109185;25.60125368295145,15.54795598202745;17.543669122835837,70.7258178169151

  ```
- **Randomize Positions Flag,** this flag is the one that specifies whether nodes are to be linked using the radius method or using the manual definition of the links.
  ```
  RANDOMIZETOPOLOGY true
  ```
- **Specify the links between the nodes,** this parameter allows for manually specifying the links between nodes. The parameter is of the form, 'node_idx,neigh0,neigh1,...' where the first entry is the node's index, then we list the indexes of the nodes it has links to. Entries are separated by a ';'. If a node has no neighbors, we must specify the node's index without any following indexes, for example '0;'.
  ```
  init.Net1.TOPOLOGY 0,1,2,3,6,8;1,0,2,3,4,5,6,7,8,9;2,0,1,3,6,8,9;3,0,1,2,6,8,9;4,1,5,7;5,1,4,7;6,0,1,2,3,8,9;7,1,4,5;8,0,1,2,3,6;9,1,2,3,6

  ```

- **The Radius of the neighborhood.** this parameter defines the radius of the neighborhood of a node, the area in which a node knows all other nodes.
  ```
  init.Net1.r 50
  ```

#### Configuration of the links between the nodes
- **The Bandwidth of a Link**. This parameter is measured in Mhz and is used in computing the cost in time of communication when offloading tasks and impacts the communication cost in the Reward function. Currently, the bandwidth is equal for all links.
    ``` 
    protocol.props.B 2
    ```
- **The Path Loss Constant of a link**. This parameter is used in computing the cost in time of communication when offloading tasks and impacts the communication cost in the Reward function. Currently, the path loss constant is equal for all links.
    ``` 
    protocol.props.Beta1 0.001
    ```
- **The Path Loss Exponent of a link**. This parameter is used in computing the cost in time of communication when offloading tasks and impacts the communication cost in the Reward function. Currently, the path loss exponent is equal for all links.
    ``` 
    protocol.props.Beta2 4
    ```
- **The Transmission Power of a node**. This parameter is measured in dbm and is used in computing the cost in time of communication when offloading tasks and impacts the communication cost in the Reward function. Currently, all nodes have the same transmission power.
    ``` 
    protocol.props.P_ti 20
    ```

- **Specify which flags have a link to the cloud**. This parameter indicates for each layer if the nodes in that layer can access the cloud or not. If the value at the index of the layer is 1, then that layer can communicate with the cloud. Otherwise, if it is 0, then the nodes in the layer are barred from communicating directly with the cloud. For example, for the configuration '0,1', the nodes in the layer of index 1 would be able to communicate with the cloud, but the nodes in the layer of index 0 would not.
    ```
    CLOUD_ACCESS 0,1
    ```

### Configuration of the Cloud
We consider the cloud as a collection of virtual machines that allow for processing multiple tasks concurrently, one per VM. Each of these virtual machines is similar to a Worker in the sense that every time-step they will be able to process a given number of instructions. Each one will need to have processing power specified.
- **Number of VMs available to the Cloud** - This specifies the number of  VMs that can process concurrent tasks in the Cloud at the same time.
    ```
    protocol.cld.no_vms 3
    ```
- **Processing Power of the VMs** - This is the number of instructions that a VM in the cloud can produce in a time step.
    ```
    protocol.cld.VMProcessingPower 1e8
    ```