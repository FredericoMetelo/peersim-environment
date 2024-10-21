# [Peersim-Env](https://arxiv.org/abs/2403.17637v2)

PeersimGym, an environment to train task-offloading MARL algorithms on Edge network simulations. The simulation behind the environment is built using the [Peersim Peer-to-Peer simulator tool](https://peersim.sourceforge.net/) that we adapted to simulate Edge Systems, and an API following the [PettingZoo Framework](https://pettingzoo.farama.org/) is provided in _Python_.

This repository contains the implementation of the PeersimGym environment, that is composed of two parts: A Gym environment that follows the API for Multi-Agent Reinforcement Leraning (MARL) proposed in Faram org's PettingZoo for MARL providing a Markov Game abstraction to the agents interacting with the environment; and, a server that wraps the simultion of the peersim environment in a REST API allowing the passing of information to the python Gym environment.

# Index* auto-gen TOC:
{:toc}

## Changelog
- Revamped the Cloud to be more logical to use.
- Fixed a bug regarding the usage of the cloud, when some nodes knew all the other nodes in the network, and others didn't.
- Added a simple visualization of the Cloud whenever the cloud is active.
- Added a new configuration to the simulation that allows for the setting of the Cloud Position.
- Tweaked the definition of the cloud properties so that the number of VMs and the default power of the VMs have separate parameters (For now all VMs will have the same processing power).



<a name="Quickstart"></a>
# Quickstart
This section will focus on two things. How to set up and utilize the environment, and how to build a new version of the simulation.
<a name="SettingUp"></a>
## Setting up
<a name="ExternalTools"></a>
### External Tools
- Install Java (Version 17.0.5)
- Install [Anaconda 3](https://docs.anaconda.com/anaconda/install/index.html) , information on setting up the environment can be found on the next sections. 
- Install [install Maven](https://maven.apache.org/install.html) (For Development of the Environment, Version 3.8.6)
  <a name="SetupAnaconda"></a>
### Setup the Anaconda environment
I provide a yml file to automatically install the necessary dependencies, see `Setup/PeersimGym.yml`. To create an environment from this specification run:
This environment is missing the PeersimGym module, which needs to be installed to use the environment in other projects. 
```
conda env create -f Setup/environement.yml
```

After creating the environment you need to activate it. This step may need to be repeated every time you open the project
(No need to create the environment again, just activate it).

```
conda activate PeersimGym
```
To install the Peersim Module we will have to add the local PeersimGym module to conda in development mode. In a terminal go from project root to `src/peersim-gym` then execute the following command:
```
conda develop . -n PeersimGym

Note: In latter versions of Conda, the conda-develop seems to have been removed, as it was deprecated for almost 2 years. Use pip instead:
pip install --no-build-isolation --no-deps -e .
```
<a name="Utilizing"></a>
## Utilizing the Environment
<a name="UsingTheEnvironment"></a>
#### Utilization Example
To start the simulation all you need to do is create a PeersimEnv object in your python code. This environment can then be used 
like a regular PettingZoo environment. 
```python
import peersim_gym.envs.PeersimEnv import PeersimEnv

env = PeersimEnv(configs={...})
actions = {agent: env.action_space(agent).sample() for agent in env.agents}
observations, rewards, terminations, truncations, infos = env.step(actions)

```
It is possible to have a custom configuration for the Peersim Simulator. To configure the environment set the configs parameter on the Peersim Environment Constructor to wither the path of the file with the configs (See the `src/peersim-gym/peersim_gym/envs/configs/config-SDN-BASE.txt` for the base file, and the config file must always include the Simulation Definitions, which are not set on the base config file), or pass a dictionary with the key the config name and the value wanted (as a string).
The following code illustrates the different ways of configuring the environment.
```python
from peersim_gym.envs.PeersimEnv import PeersimEnv
# There are 3 options to configure the environment.

# Option 1 (RECOMENDED): Passing a Dictionary with the key as the property being configured and the value a string with the value of the property. 
configs_dict = {"protocol.mng.r_u": "999", "protocol.props.B": "1"}

# Option 2: Passing a Path to a configuration file. 
configs_dict="/home/fm/Documents/Thesis/peersim-srv/configs/examples/default-config.txt"

# Option 3: Passing the None value, all the configurations will be the default. The default value for each configuration is the value on the examples of each of the properties.
configs_dict = None  # Default Value

# Just pass the configuration in the configs parameter. 
env = PeersimEnv(configs=configs_dict, log_dir="")
```
Internally we always pass a config file to the Peersim simulation tool.
When the configs are passed as a `Dict` or `None` a configuration file is generated. The details of the possible configurations are in the next section.


<a name="Configurations"></a>
## Configurations of the Model

To develop our simulation we utilized the Peersim simulation engine, which allows for large-scale simulations with high dynamicity to be run. Furthermore, the Peersim Engine allows for customizable and in-depth configuration of the simulation through configuration files. A feature we find very useful.

In the subsequent section, we will delve into a comprehensive explanation of the simulation configurations.


<a name="ConfiguringTheSimulation"></a>
### Configuration of the Simulation
Before beginning to explain the possible configurations of the different parts of the network we need to go over what these parts are. The simulation works by first generating a set of nodes that can be of two different types, Workers and Clients. The Clients act as task generators of work and collect metrics on the conclusion of tasks. Whereas the Worker Node acts as a processing node for said tasks, meaning the Worker node is the one doing the actual computing of the tasks. The Worker nodes also may host a controller component, which is a protocol that will run on the same node of the workers and is responsible for collecting information about the node's neighborhood and sending information about the node's neighborhood in one-hop broadcasts. 

The challenge of task offloading encompasses various configurations, and we have implemented some of these specific instances. These include Online-Binary task offloading, where the decision to offload the next available task is made dynamically; and Batch Task offloading, where, at each time step, decisions are made for every task that arrived in the last time-step regarding offloading. Furthermore, we are working on implementing a dependency-cognisant task offloading simulation.
The selection of these modes is done by defining a base file that outlines the appropriate protocols for each instance of the problem. Beyond determining the simulation mode, the configurability extends to a range of global parameters applicable to all instances, in the remainder of this section we will be going over the different configurations that are allowed.

The global configurations encompass six domains: Global Configurations, Worker-specific configurations, Controller-specific configurations, Client-specific configurations, Cloud-specific configurations, and lastly, topology and network-specific configurations.


### Global Configurations
#### Overall configurations of the simulation
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
- **Define what layers can be clients**, this parameter specifies the layers that have clients generating tasks.
  ```
  clientLayers 0,1
  ```

- **The flag that manages the existence of the Cloud** This flag can take values of either 1 or 0. If the flag is set to 1 an extra node not included in the nodes specified from 'SIZE' is created. This means that if 'SIZE 10' and 'CLUD_EXISTS 1' then there is a total of 11 nodes.
    ```
    CLOUD_EXISTS 1
    ```
- **Set the random seed** This value set's the value for the random seed in the simulation. This is used to ensure that the simulation is reproducible. Can be changed by the environment at each episode.
    ```
    random.seed 1234567890
    ```
<a name="ConfiguringTheController"></a>
#### Configurations of the DiscreteTimeStepManager and PettingZoo environment
- **Utility Reward..** a parameter of the reward function acts as a weight in the expression that computes the utility of a reward. This parameter receives an int. It is directly used by the environment and not the simulation.
    ```
    utility_reward 1
    ```

- **Delay Weight.** A parameter of the reward function acts as a weight in the expression that computes the cost associated with the delay induced by taking an action. This parameter receives an int. It is directly used by the environment and not the simulation.
    ``` 
    delay_weight 1
    ```

- **Overload Weight..** a parameter of the reward function acts as a weight in the expression that computes the cost associated with node overloading (overloading of a node happens when the node has too many tasks assigned and starts losing tasks) induced by taking an action.
This parameter receives an int. It is directly used by the environment and not the simulation.
    ``` 
    overload_weight 150
    ```
- **Frequency of Actions** The frequency of actions is the number of time steps that the environment will wait before awaiting a new set of actions to be distributed to the controllers in the simulation.  
   ```
      protocol.mng.cycle 1
    ```

<a name="ConfiguringTheClient"></a>
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
- **Self Generated Tasks** Specifies if the clients generate tasks only the worker in the same node or if they send tasks to all the workers in their neighborhood. Takes value 1 if true or 0 if false.
    ```
    clientIsSelf 1
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
<a name="ConfigurationOfTheWorker"></a>
The workers are defined in sets of nodes with the same properties, which we call layers. The layers are organized in a vector and for each layer there is a processing power, that consists of the number of available cores multiplied by the frequency of the worker's CPU, and a maximum queue size. We can add heterogeneity to the nodes in a layer by specifying a deviation term to the frequency of each CPU in that layer.

Nodes can only communicate with nodes on its layer, the layer immediately below and the one immediately above, for example, a node in layer index 1, would only be able to communicate with nodes in the layer index 0 and index 2. This can be overridden by manually specifying the links of the network, we shall explain how to do this later. Only nodes in layer 0 can communicate directly with the clients.

- **Specify the number of layers in the simulation,**  This flag informs the simulation of the total number of layers to be created. The value of this flag must be equal to the number of entries in the 'NO_NODE_PER_LAYER'.
    ```
    NO_LAYERS 2
    ```
- **Specify which layers can get tasks directly from the clients** This parameter specifies the layers that can receive tasks directly from the clients. The layers are separated by a ','. 
  Regarding the interaction with clientIsSelf, if clientIsSelf is 1 and a given client's layer is in layersThatGetTasks, then that client would be generating tasks for itself, all the clients in layers not in layersThatGetTasks would not receive any tasks. Layers that get tasks is a coma separated list of the layers id. IE if layers 0 and 1 get tasks then, in the configs, you would have:


    ```
    layersThatGetTasks 1
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
        
<a name="ConfigurationsOfTheLinks"></a>
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
- **Define the types of channels**. This parameter specifies classes to be used for the types of the channels that will be available in the simulation, for example a wireless channel and a wired channel. The channel types are represented by the class name that is to be used, and are defined as a list of "," separated values.
    ``` 
    protocol.urt.channelTypes PeersimSimulator.peersim.env.Transport.OpticalFiberSNR;PeersimSimulator.peersim.env.Transport.WirelessSNR
    ```
  Note:Currently all channel types available are in the example above. WirelessSNR being hte original wireless channel with the Shannon-Hartley theorem and considering physical factors like the antenna gain, and the OpticalFiberSNR being a channel that uses the same theorem but with specifiable SNR.
  
- **Define the channel type used to communicate between different layers**. This parameter specifies the channel type used to communicate between different layers. The values are the indexes of the channel type to be used in the `protocol.urt.channelTypes`, I.E. in the example above 0 would represent the wirelessSNR.
  The ChannelTypesPerLayers is defined as a matrix of layers x layers (IE with dimensions NO_LAYERS x NO_LAYERS). You have the index of the channel type, in ChannelTypes, between layer i and j in `ChannelTypesPerLayers[i][j]`. The channel types per layer is defined in the configs as with a string where values in a row are "," separated, and different rows are ";" separated. 
    ``` 
    protocol.urt.channelTypesBetweenLayers 0,0,1;0,0,1;1,1,1
    ```
  
- **Specify which flags have a link to the cloud**. This parameter indicates for each layer if the nodes in that layer can access the cloud or not. If the value at the index of the layer is 1, then that layer can communicate with the cloud. Otherwise, if it is 0, then the nodes in the layer are barred from communicating directly with the cloud. For example, for the configuration '0,1', the nodes in the layer of index 1 would be able to communicate with the cloud, but the nodes in the layer of index 0 would not.  
    ```
    CLOUD_ACCESS 0,1
    ```

### Configuration of the Cloud
We consider the cloud as a collection of virtual machines that allow for processing multiple tasks concurrently, one per VM. Each of these virtual machines is similar to a Worker in the sense that every time-step they will be able to process a given number of instructions. Each one will need to have processing power specified.
Notably, the parameters here are only relevant if the cloud is enabled in the simulation.

- **Number of VMs available to the Cloud** - This specifies the number of  VMs that can process concurrent tasks in the Cloud at the same time.
    ```
    protocol.cld.no_vms 3
    ```
- **Processing Power of the VMs** - This is the number of instructions that a VM in the cloud can produce in a time step.
    ```
    protocol.cld.VMProcessingPower 1e8
    ```
- **Selecting the position of the Cloud** - This parameter specifies the position of the cloud in the network. The coordinates of the cloud are specified in the format "X,Y".
    ```
    CLOUD_POS 50,50
    ```
  
### Configuration specific to the Ether Topology integration
- **Ether Configuration Flag** Indicates to the simulation that the ether topology is to be used.
    ```
    MANUAL_CONFIG true
    ```
- **Ether Cores Default**: Specifies the number of cores in each node in the ether topology. The value for each layer is separated by a ','. For example, given the value '4,8', the nodes in the layer index 0 will have 4 cores and the nodes in the layer index 1 will have 8 cores.
    ```
    MANUAL_CORES 4,8
    ```
- **Ether Frequencies Default** Specifies the frequency of the CPU of each node in the ether topology. The value for each layer is separated by a ','. For example, given the value '1e7,3e7', the nodes in the layer index 0 will have a frequency of 1e7 instr/second and the nodes in the layer index 1 will have a frequency of 3e7 instr/second.
    ```
    MANUAL_FREQS 1e7,3e7
    ```
- **Ether Q Maxes Default** Specifies the maximum queue size of each node in the ether topology. The value for each layer is separated by a ','. For example, given the value '10,50', the nodes in the layer index 0 will have a maximum queue size of 10 and the nodes in the layer index 1 will have a maximum queue size of 50.
    ```
    MANUAL_QMAX 10,50
    ```
  
### Configuration specific to the Trace Generation integration
-**Workload File** - This parameter specifies the path to the json file that contains the workload to be used in the simulation. 
    ```
    protocol.clt.workload_file /home/user/peersim-env/configs/examples/trace.json
    ```

# Developed Environment
As previously stated the PeersimGym environment has two major components, the first is a simulaiton supported by the Peersim P2P Simulaiton Tool \[1\] developed in Java. The second is a python module that implements the PettingZoo API and provides a Markov Game abstraction of the simulation to the agents interacting with the environment. To enable interaction between the python module and the simulation we wrap the simulaition in a Spring Boot Rest Server and use REST request from python to interact and manage the simulation. We will now begin explaining the inner-workings of the different components mentioned.

<a name="HowTheSimulationWorks"></a>
## Simulation
### Peersim P2P Tool programing model
The Peersim simulaiton works by executing a collection of pre-specified protocols from the Applicaiton, Transport and Link Layers using one of two-engines. The Cycle driven engine that executes all protocols at every time-step. And, the Event driven engine, that works by scheduling events. The Event driven engine allows the scheduling of protocols to run at every time-step, effectively covering the funcitonality of the Cycle Driven engine while still permitting the usage of events. It's the latter engine that we use in our simulation. 
There are two methods that define the behavior of the applicaiton layer protocols, these are:
```Java
/*
  Defines the behavior to be executed once at each time-step.
*/
void nextCycle(Node node, int protocolID);

/*
  Event handling funciton that receives an event and executes the appropriate method to handle it.
*/
void processEvent(Node node, int pid, Object event);
```

Configurations to the Peersim simulator are passed through a configuration file when starting a simulation.

When creating the simulation one of two mechanisms is used to place the nodes and define the topology. For placing the nodes there is the random method, that randomly samples from a uniform distribution to place the nodes in a 100x100 square. And, the manual placement that allows defining the position of each node in the network through the configuration file. For defining the neighbourhoods of the nodes there are two methods, the first is by defining a radius of a circumference. All nodes within the circumference can observe each other. The second is by specifying the neighbourhoods of each node through the configs.


### The Simulation Model
The simulation is logically divided into discrete time slots of a unit size $t=1,..., T$. Following the natural flow of the Peersim P2P simulaiton tool, we define a set of protocols in the applicaiton layer that will interact with each other and simulate an edge network over teh $T$ time-steps that the simulation will last. The involved protocols are:
- Client
- Worker
- Controller
- Cloud
The structure of the simulator assumes a set of client devices $\mathcal{C}$, generating data to be processed, similar to sensors in IoT devices. This data is then sent to a worker node, which can either process the task locally or offload it to other nodes with available resources. Each device $w_i$, in a set of worker nodes $\mathcal{W}$, is characterized by the following features, which can be included in the system or neglected

#### Client

 The client protocol is responsible for application creation and distribution within the network. It employs a Poisson process to determine the allocation of tasks to neighbors, with eligibility for task receipt specified in the system configuration. This probabilistic approach ensures a realistic simulation of task dissemination behavior observed in Edge Computing scenarios. The Client also manages the completion of tasks, focusing on registering relevant metrics upon the conclusion of tasks sent to workers. 
The workload in the simulation takes the form of applicaitons, that are represented as Directed Acyclic Graphs (DAGs) of tasks, there may be one or more tasks in an application. When generating a task the clients randomly sample from a list of available task types that can be defined in the configurations, or if utilizing the plausible realistic workload mechanims that we elaborate in a latter section a file containing all the possible task types.

__Components:__

- **Sent Task ID List**, the client maintains a queue with information about the tasks sent including the time-step of sending.
-  **Position**, $l_i$, which also affects the communication delay and other proximity-based mechanisms.
-  **Transmission power**, $P_i$, that affects the communication delays.

__Behavior:__

All the client implementations must implement the `PeersimSimulator.peersim.env.Nodes.Clients.Client` interface.

At each time-step the client checks which neighbouring workers should receive Applicaiotons. If a worker is to receive an application, the client generates an application and sends it to the worker.

The client event handling considers a single event:
- Application Conclusion Event - Where the client must register the application as finished and track the appropriate metrics.

 Other than the basic behaviour methods mentioned when explaining the Peersim simulation model the other notable method is the:
```Java
/* Generates an applicaiton*/
Application generateApplication(int target);
```

**Applicaiton and Task Model**
The applicaion is defined as a DAG of tasks $\langle T, D \rangle$, where $T$ are the tasks and $D$ the dependencies among them.
But, the tasks are both the unit of offloading and of scheduling. A task $\tau_i$ is a computational requirement generated by a client and is represented as a tuple  $\tau_i = \langle i, \rho_i, \alpha_i^{\text{in}}, \alpha_i^{\text{out}}, \xi_i, \delta_i \rangle$, where
- $i$ is a unique identifier of the task;
- $\rho_i$ is the number of instructions to be processed;
- $\alpha_i^{\text{in}}$ is the total data size of the input;
- $\alpha_i^{\text{out}}$ is the data size of the output/results;
- $\xi_i$ is the cost in CPU cycles per instruction;
- $\delta_i$ is the deadline of the task, i.e., the maximum allowed latency for returning the results (see Tab.~\ref{tab:notation}).



#### Worker
 Central to our simulation, the worker protocol governs the processing mechanics of tasks within the network.
 The set of workers $\mathcal{W}$ can be divided into tiers/layers. All nodes within a tier are generated with similar resource specs. The tier/layer system is useful for modeling hierarchical networks like in the Fog Computing paradigm. Optionally, a user can include a cloud server in the system. The cloud does not have a maximum queue capacity and consists of multiple virtual machines (VM) that are assigned to tasks in the order they arrive.
Hence, the inclusion or exclusion of tiers or clouds allows one to cover a wide range of structures, from a decentralized peer-to-peer (P2P) to a hierarchical n-tier network.

 __Components__

-  **Task queue**, $Q_i$, a structure that allows at most $Q_i^{\text{max}}$ received tasks to be stored and await to be processed in a first-in-first-out fashion. Any tasks received above the capacity of the node will be dropped.
-  **Number of processors**, $N_{\phi}^i$, of frequency, $\phi_i$, in $MHz$. Therefore, the node can process $N_{\phi}^i\phi_i$ instructions per time step. Tasks are processed sequentially, and all the available cores are utilized when processing a single task. Upon completion, the node will send the result to the origin ---  either the client that generated the task, or to other node from where the task was received, which will repeat the process until the client receives the results.
-  **Transmission power**, $P_i$, that affects the communication delays.
-  **Position**, $l_i$, which also affects the communication delay and other proximity-based mechanisms.
 
 __Behavior__

At every time-step the worker processes the first task available in it's queue. In practice this works by updating a task-specific instruction counter to track to track the task's progress. Completion of a task triggers the protocol to return the result to the originating client. This return path may involve multiple hops, particularly for tasks that have been offloaded across several nodes.  Should the queue of the worker deplete, it transitions into an idle state, awaiting new tasks. The worker also engages in routines for handling offloaded tasks from other nodes, new tasks directly from clients, and results of concluded tasks. If space permits, tasks are added to the queue for processing; otherwise, they are dropped. Completed tasks are either directed back to the origin client or offloaded to closer workers for final delivery. 

 The worker protocols must be prepared to deal with four types of events:
  -  New Application arrival - Handle the arrival of an application with its \gls{DAG} of tasks to be processed in the node.
  -  Task Offloading Reception Event - Handle the receiving of an offloaded task. 
  -  Task Conclusion Event - Handle the conclusion of the task. Which may involve the conclusion of an application.
  -  Federated Learning Update Exchange  - Handle the communication of \gls{FL} Update to a neighbor. Possibly taking part in a chain of redirects.

 
#### Controller

The controller is tightly coupled with a worker protocol and monitors the worker's state.


__Components:__

- **List with information about the neighbours**, one of the main responsibilities of the controller  is maintaining information about the neighbourhood of the related worker.

__Behavior:__

At every time step if a state alteration is detected, the controller initiates a one-hop broadcast to disseminate the updated state information to neighboring nodes. Maintaining an updated network state and facilitating informed decision-making for task offloading. It also acts as the bridge between the simulation and the RL agents, passing the offload instructions to the worker, virtually representing the agents, we shall refer to the offload decisions as if they are made by the Controller. Furthermore, the Controller also handles the Neighbour State update event, ensuring the node updates its information regarding the state of neighboring nodes as necessary.

The controller protocols must be prepared to deal with four types of events:
-  Neighbour State Update Event - The Controller receives information from one of its neighbors containing information about its state, required for making informed decisions when offloading. The controller must maintain an updated view of the neighborhood based on the communicated information.


#### Cloud
We consider the cloud as a collection of virtual machines that allow for processing multiple tasks concurrently, one per VM. Each of these VMs is similar to a Worker in the sense that every time-step they will be able to process a given number of instructions. Each one will need to have processing power specified. The only event the cloud has is the task offloading event, which similar to the Worker, handles the receiving of an offloaded task. The most notable thing about the cloud is that it exists on a node that exclusively hosts the Cloud Protocol and there is only one such node in the entire system.

__Components:__
- ** List of virtual machines**, the cloud maintains a list of worker like virtual machines each capable of processing tasks.

__Behavior__
At each time-step the cloud processes one task per VM while tasks are still availble.





#### Other Simulation Models

**Communication between the agent and the environment.**<br>
As the clients generate tasks and the workers receive them, nodes with a _controller protocol_ might decide to offload tasks instead of processing them locally, and the controller protocols will share their local information through the one-hop broadcasts. All the communication in the network, i.e., offloading of the tasks between the nodes, is assumed wireless. To compute the number of bits sent per second, we consider the _Shannon-Hartley_ theorem, where the latency to communicate $\alpha$ bits from node $w_i$ to  $w_j$ is
$$
T^{\text{comm}}_{i,j}(\alpha) =\frac{\alpha}{B_{i,j} \cdot \text{log}(SNR)},
$$
where $B_{i,j}$ is the channel bandwidth.

The communicaitons utilize one of two mechanisms, the first is based around the ```PeersimSimulator.peersim.env.Transport.WirelessSNR``` class, and involves computing the delay of a channel using an object that implements the ```PeersimSimulator.peersim.env.Transport.SNRCalculator```, that will specify the concrete way to compute the SNR. Currently there are two mechanisms in this category, one that computes the SNR based on a wireless channel between the nodes using: $SNR =1 + 10^{\frac{P_i + G_{i,j} - \omega_0}{10}}$, here $G_{i,j}$ is the channel gain, $P_i$ is the power of the node's transceiver, $\omega_o$ and $\eta$ are the noise power and a constant representing the noise power spectral density respectively, s.t. $\omega_0 =  \eta + 10 \log_{10}(B_{i,j})$. And the other that allows defining the SNR direcly.

The second mechanism to define the delay of a communicaiton is by utilizing  ```PeersimSimulator.peersim.env.Transport.GenericByteRateBandwidthTransport``` that utilizes a bit/rate to define the delay of sending a message between two nodes.


### Spring Boot REST Wrapper
To permit communication between the simulation in Java and the environment in Python, with Spring Boot we can seamlessly integrate the Peersim Simulation within a _REST API_ server. This will allow the environment to execute the actions taken by the agents and propagate the federated learning upgrades to the Java simulation by doing a _POST/action_ request, and getting the state of the environment and other information by making _GET:/state_ requests. Furthermore, the FL updates are sent through the simulation through a _POST:/fl/updates_ and the arrived update are obtained through the endpoint _GET:/fl/state_.

To permit the communicaiton we manage the flow of the simulaiton using the Simulation Manager Protocol, that will at a configurable interval stop the simulation and await a request with an action to be posted to the _Spring Boot_ server. It will then distribute the actions described in the post actions request and resume the simulation until the interval to stop has passed again.

To be able to have the agents interact with the simulaiton a special meta-protocol is run in the simulation , ```PeersimSimulator.peersim.env.Nodes.SimulationManager```, that will at a configurable interval stop the simulation and await a request with an action to be posted to the _Spring Boot_ server. It will then distribute the actions described in the post actions request and resume the simulation until the interval to stop has passed again.

## PettingZoo Interface
### PettingZoo interface for PeersimGym
PettingZoo \[2\] is a Python library that provides a unified interface for a collection of multi-agent environments. It is designed to facilitate the development of multi-agent reinforcement learning algorithms by providing a common interface for interacting with a variety of environments. The library includes a wide range of environments, from simple grid-world games to complex simulations of real-world systems. PettingZoo is designed to be easy to use and flexible, allowing researchers to quickly prototype and test new algorithms in a variety of environments.
There are four main methods that are required to be implemented in the PettingZoo environment, these are:
- **reset()** - This method is called at the beginning of each episode to reset the environment to its initial state and return the initial observation. In our case, this method will change the seed of the simulation random number generator if configured to do so, reset the simulation to its initial state and return the initial state of the network.
- **step(action)** - This method is called to take a step in the environment. It receives an action as input and returns the next observation, reward, done flag, and any additional information. The simulation is configured to stop at pre-defined intervals and await an action to be posted to the Spring Boot server, the action is then distributed to the simulation and the simulation is resumed until the next interval to stop. PeersimGym utilized the fully parallel version of PettingZoo, so all agents that are acting in that step post an action at the same time.
- **render()** - This method is called to render the environment for visualization purposes. It can have multiple render modes. In our case there are two render modes: `ascii` that prints all the environment information to the console; and, `human` that utilizes a visualization mechanism that we elaborate on latter . 
- **close()** - This method is called to close the environment and release any resources that it may be using. It is optional and can be implemented if necessary.

<a name="MDP"></a>
### Markovian Game
## Markov Game

Reinforcement Learning (RL) is usually modeled as a Markov Decision Process (MDP), with a sequence of states such that the Markov Property holds, i.e., the next state $ s_{t+1} $ depends exclusively on the current state $ s_t $ and the performed action $ a_t $. When including multiple agents, most of the MDP convergence properties do not hold [3]. Therefore, we must formulate our problem as a Markov Game (MG) [3]. In our setting, in a network of $ N $ nodes, an MG is represented as a tuple $ \langle n, \mathcal{S}, \mathcal{A}, P, R \rangle $, where:
- $ n $ is the number of agents (i.e., nodes with a controller protocol),
- $ \mathcal{S} $ is the state space,
- $ \mathcal{A} $ is the action space,
- $ P $ is an (unknown) transition probability function,
- $ R $ is the reward function.

We provide a table with the notation used in the following table.

### State Space

Different Edge System settings will have different definitions of state and action space, and shaping the reward will decide what are the important and less important objectives for the agent. In this paper, we consider a wide set of state spaces that can be customized by excluding or combining specific options to model the system of interest. At time step $ t $, each node will broadcast its local state to each of its neighbors and receive their local states to build a local state representation before deciding on the action. The state space is represented by a tuple:

$$
\mathcal{S} = (\mathcal{I}, \mathcal{K}, \mathcal{Q}^t, \mathcal{F}, \mathcal{L}, \{\mathcal{B}_1, \dots, \mathcal{B}_n\}, \{\mathcal{P}_1, \dots, \mathcal{P}_n\})
$$

where:
- $ \mathcal{I} = \{1, \dots, n\} $ is an array with the IDs of all the nodes in the network. All the following arrays respect the ordering of the IDs in this array;
- $ \mathcal{K} = \{\kappa_1, \dots, \kappa_n\} $ is an array specifying the layer/tier for each node;
- $ \mathcal{Q}^t = \{Q^t_1, \dots, Q^t_n\} $ is an array storing the queue size for each node in the network, at time step $ t $;
- $ \mathcal{Q}_i^{\text{max}} = \{Q_1^{\text{max}}, \dots, Q_n^{\text{max}}\} $ is an array storing the maximum capacity of the queues for each node in the network;
- $ \mathcal{N}_n $, an array with the IDs of the nodes that belong to node $ n $'s neighborhood;
- $ \mathcal{F} = \{N_{\phi}^1 \phi_1, \dots, N_{\phi}^n \phi_n\} $ is an array storing the processing power for each node in the network;
- $ \mathcal{L} = \{l_1, \dots, l_n\} $ is an array storing the position for each node in the network;
- $ \mathcal{B}_i = \{B_{i,1}, \dots, B_{i,n}\} $ is an array storing the bandwidth of the channels for node $ w_i $ to all its neighbors;
- $ \mathcal{P}_i $ is the transmission power of node $ w_i $’s antenna.

The size of the state vector will define the input size of a DRL agent; hence, in this version of the simulator, the state dimension needs to stay consistent throughout iterations.

### Action Space

The Action Space, denoted as $ A $, is an array that includes the individual action spaces for each agent, represented as:

$$
A = \langle A_1, \dots, A_n \rangle
$$

The action $ a_t \in \{1, \dots, N\} $, taken by the node indexed by $ t $, represents the index of the destination node within node $ t $'s neighborhood. This destination can either be one of the neighboring worker nodes or the node itself if it decides to process the task locally. We use $ a_t $ interchangeably to denote both the index of the destination node and the act of sending a task to that node,

## The PeersimGym environment modules
The PeersimGym environment is implemented in Python and uses the PettingZoo inteface to provide a Markovian abstraction of the Peersim simulation. The PeersimGym environment is composed of one main module, the PeetingZoo environment class that implements the aforementioned methods required by the petting zoo interface and builds the rest requests required to interact and manage the simulation. Other than the base PeetingZoo environment class there are other three modules that are used to implement the PeersimGym environment. These are:
- ThreadManager: The actual simulation is implemented in Java and runs in a separate thread. The ThreadManager is responsible for starting the simulation, stopping it, and managing the communication between the simulation and the Python environment.
- ConfigManager: Is responsible for obtaining the configuration medium and setting up the config file for the simulation. It is also responsible for obtaining the required information from the simulation config file required by the Python environment.
- Visualization Tool: The visualization module is responsible for rendering the environment in a human-readable format. It is used to visualize the environment in the human render mode. The visualization tool is implemented in Python and uses the Pygame library to render the environment.

Furthermore we provide two special modules to improve the realism of the simulation, these are:
- Realistic Workload Generator: This module generates a realistic workload for the simulation. The workload is generated based on a trace file that contains information about the tasks to be generated. The trace file is generated using the Alibaba Cloud trace generator tool \[5\]. The module reads the trace file and generates the tasks based on the information in the trace file.
- Realistic Topology Generator: This module generates a realistic topology for the simulation. The topology is generated based on a trace file that contains information about the nodes and their connections. The trace file is generated using the Alibaba Cloud trace generator tool \[4\]. The module reads the trace file and generates the nodes and their connections based on the information in the trace file.

<a name="Developing"></a>

# Developing the Simulation
<a name="SetupTheSimulator"></a>

## Implementing your own protocols for the Simulation
<a name="CustomizingSim"></a>
PeersimGym's simulator allows for in-depth customization by changing the very protocols that model the workers, clients, and controllers.

To implement your own protocol you need to create new classes that extends the `PeersimSimulator.peersim.env.Nodes.Workers.AbstractWorker`, `PeersimSimulator.peersim.env.Nodes.Controllers.AbstractController` or `PeersimSimulator.peersim.env.Nodes.Clients.AbstractClient` classes. See the classes in question for the methods that need to be implemented, we provide detailed descriptions of each in the Javadocs.

Note: In-depth documentation is still a work in progress, so not all methods are properly documented yet.

Furthermore, it is also possible to add action types by implementing the `PeersimSimulator.peersim.env.Records.Actions.Action` interface, and it's possible to retrieve different information from the network by implementing different `PeersimSimulator.peersim.env.Records.SimulationData.SimulationData`, note that this would reuire implementing Controller and Worker protocols that are capable of dealing with the new types. When implementing new action types or simulation datas, the new classes will need to be registered in the respective interface to be able to marshal them with jackson, see the interfaces in question for examples.
### Setup the simulator
I provide a jar with everything needed to run the project in `target/peersim-srv-0.0.1-SNAPSHOT.jar`. The following sections
are only to compile any changes made over the version provided in this repository.

<a name="MavenDependencies"></a>
### Maven Dependencies
**Setup Maven**
First install maven itself, if you don't have already. To install it follow the instructions in https://maven.apache.org/install.html
After having maven installed. Add the private dependencies that come packaged with the Peersim project to maven.
To do that run the provided script that will add the dependencies to a local private repository:

**Linux**

```bash
./install-mvn-external-dependencies.sh
```

**Windows**

```powershell
.\install-mvn-external-dependencies.ps1
```

<a name="CompilingTheSimulator"></a>
### Compiling the Simulator
To compile the simulator use the following command:

```bash
mvn clean -Dmaven.test.skip package
```

This will package a jar with all the necessary dependencies to run the simulator. The jar can be found on the `target/peersim-srv-0.0.1-SNAPSHOT.jar`
directory/folder.

<a name="Bibtex"></a>
# PeersimGym Bibtex
@article{metelo2024peersimgym,
      title={PeersimGym: An Environment for Solving the Task Offloading Problem with Reinforcement Learning}, 
      author={Frederico Metelo and Stevo Racković and Pedro Ákos Costa and Cláudia Soares},
      year={2024},
      eprint={2403.17637},
      archivePrefix={arXiv},
      primaryClass={cs.LG}
}

# References
1. Alberto Montresor and Márk Jelasity. "PeerSim: A Scalable P2P Simulator." Proceedings of the 9th International Conference on Peer-to-Peer, 2009, pp. 99-100.
2. Ann Nowé, Peter Vrancx, and Yann-Michaël De Hauwere. "Game Theory and Multi-agent Reinforcement Learning." In Reinforcement Learning: State-of-the-Art, edited by Marco Wiering and Martijn van Otterlo, 2012. ISBN: 978-3-642-27645-3.
3. Justin K. Terry, Benjamin Black, Ananth Hari, Luis S. Santos, Clemens Dieffendahl, Niall L. Williams, Yashas Lokesh, Caroline Horsch, and Praveen Ravi. "PettingZoo: Gym for Multi-Agent Reinforcement Learning." CoRR, vol. abs/2009.14471, 2020.
4. Thomas Rausch, Clemens Lachner, Pantelis A. Frangoudis, Philipp Raith, and Schahram Dustdar. "Synthesizing Plausible Infrastructure Configurations for Evaluating Edge Computing Systems." In Proceedings of the 3rd USENIX Workshop HotEdge, 2020.
5. Huangshi Tian, Yunchuan Zheng, and Wei Wang. "Characterizing and Synthesizing Task Dependencies of Data-Parallel Jobs in Alibaba Cloud." In Proceedings of the ACM Symposium on Cloud Computing (SoCC), 2019.
