# Peersim-Env
This repository contains the implementation of Peersim-env. This is composed of two parts. A Gym environment python class, and
a server that wraps the simultion of the peersim environment in a REST API allowing the passing of information to the python Gym environment.
# Index
1.[How The Simulation Works](##HowTheSimulationWorks)<br>
&nbsp;1.1.[The Simulation Server](###TheSimulationServer)<br>
&nbsp;1.2.[MDP](###MDP)<br>
&nbsp;1.3.[Network Topology](###NetworkTopology)<br>
&nbsp;1.4.[REST API](###RESTAPI)<br>
2.[Quickstart](#Quickstart)<br>
&nbsp;2.1.[Setting Up](##SettingUp)<br>
&nbsp;2.1.1.[External Tools](###ExternalTools)<br>
&nbsp;2.1.2.[Setup Anaconda](###SetupAnaconda)<br>
&nbsp;2.2.[Utilizing the Environmnet](##Utilizing)<br>
&nbsp;&nbsp;2.2.1.[Utilization Example](###UsingTheEnvironment)<br>
&nbsp;&nbsp;2.2.2.[Configurations](##Configurations)<br>
&nbsp;&nbsp;2.2.3.[Configuring The Simulation](###ConfiguringTheSimulation)<br>
&nbsp;&nbsp;2.2.4.[Configuring The Controller](###ConfiguringTheController)<br>
&nbsp;&nbsp;2.2.5.[Configuring The Client](###ConfiguringTheClient)<br>
&nbsp;&nbsp;2.2.6.[Configuring The Workers](###ConfigurationOfTheWorker)<br>
&nbsp;&nbsp;2.2.7.[Configuring The Links](###ConfigurationsOfTheLinks)<br>
&nbsp;2.3.[Developing the Simulation](##Developing)<br>
&nbsp;&nbsp;2.3.1.[Setup The Simulator](###SetupTheSimulator)<br>
&nbsp;&nbsp;2.3.2.[Maven Dependencies](###MavenDependencies)<br>
&nbsp;&nbsp;2.3.3.[Compiling The Simulator](###CompilingTheSimulator)<br>

<a name="HowTheSimulationWorks"></a>
## How the Simltion Works
<a name="TheSimulationServer"></a>
### The Simulation Model
We build a simulation based on the Network used in the paper by J.-y. Baek, G. Kaddoum, S. Garg, K. Kaur and V. Gravel, "[Managing Fog Networks using Reinforcement
Learning Based Load Balancing Algorithm](https://ieeexplore.ieee.org/document/8885745)". The tool we use to support our simulation is the [Peersim Simulation Tool](https://peersim.sourceforge.net/).

**Simulation Components**<br>
We simulate a static network (we are planning to make the topology configurable like in an actual SDN in later updates (Still not implemented!)). All the Nodes in the Network know each other from the start and communicate through a reliable (can be configured to be unreliable) link.
The Network simulated has three types of nodes, that are:

- Worker Nodes: Simulate the execution of tasks, offload tasks on request by the controller and communicate status messages to the controller.
  - Controller Node: Some worker nodes have the controller function. This allows them to keep information on the status of each node in their
  neighbourhood and receive offload instructions from the agents outside the simulation, and subsequently pass them to the relevant worker node. The worker node to be offloaded to is selected on a round robin style across all nodes.
- Client Nodes: Only one client that will for each node generate and request tasks following a poisson process. the Client also keeps an
  average of the time the requests took to be processed in the time internal to the simulator.

**Simulating the passage of time**<br>

To simulate the passage of time we make use of the built-in schedulling properties of the Peersim simulation tool, by utilizing 
having our protocols extend the CDScheduler Class and execute something at each time-step, each simulation time-step represents
a second, and all the protocols execute at each time-step.
We have a special protocol, DiscreteTimeStepManager that controls the stopping of the simulation, so that the agents can send
their offloading instructions to the simulation.
The other protocols also execute every time-step, but none of them affects the flow of the simulation. Their functions encopass

The worker at each time-step processes the tasks it has received by how many instructions per second it can do. If any changes 
to its internal state happened it will broadcast its state information to its neighbourhood. 

The clients will generate and send tasks to the nodes following a Poisson Process, every time-step they check what workers
are scheduled to receive a task. They then generate that task and send it to the worker. For every worker that was sent a task the client node will use the poisson process to
see on how many time-steps it will have to send the next task to said worker.

**Communication between the agent and the environment.**<br>
To allow communicating with external agents we wrap the agent up in a Local Spring Server that exposes a REST API for the agent to send actions to the environment and observe it's state.

<a name="MDP"></a>
### MDP 

```
TODO: This has basically not changed, except there is a list of states and actions in the action space instead! I didn't
 alter this yet as I'm execting it will change a lot in the future.

TODO: This part of the explanation is basically paraphrasing (and times directly quoting) what the authors have said
in the paper "Managing Fog Networks using Reinforcement Learning Based Load Balancing Algorithm".
 
I wanted to repurpose this in my chapter about the environment developed, but to be honest I am reluctant
to do it as I fear this might count as plagiaism. 
But I also do not see a way around this as I am trying to implement and solve the MDP in the paper. Is there a better
way of going around this. Is this Plagiarism?
```

To be applied to Reinforcement Learning the problem needed to be specified as an Markov Decision Process. Utilize the MDP definition 
for a Load Distribution Problem in a Network with a central controller node following closely the definition of MDP the authors at "[Managing Fog Networks using Reinforcement
Learning Based Load Balancing Algorithm](https://ieeexplore.ieee.org/document/8885745)" give.

The MDP is defined as a tuple <S, A, P, R>, the explanaition the authors give to the elements of this tuple is:
- **State Space, S**: S is the set of all possible states, these states are tuples $s = (n^l, w, Q)$. 
  - $n^l$ is the index of the node that is currently being evaluated for offloading. $n^l \in N \land 1 \leq n^l \leq N$
  - W, as the authors define, is the number of tasks to be allocated per unit of time. I consider this to be the average number of
    tasks being processed per unit of time in the node $n^l$. This value is  $w \in N ∧ 1 \leq w \leq W_{max}$
  - Q represents the current state of all node queues, how many tasks each has. $Q = {(Q1, ..., QN)|Qi \in 0,...,Q_{max\_size}}$
- **Action Space, A**: A is the set of all possible actions to be taken for the node currently being evaluated. The
  An action is a pair of the form $a = (n^o, w^o)$.
  - $n^o$ is the index of a node neighbour to the node currently being evaluated. $n^o \in N ∧ 1 \leq n^o \leq N$
    n^o will also never be a node that has bigger queue length than the current node. `TODO Guarantee this n^o property!!!`
  - $w^o$ is the number of tasks being offloaded to a neighbouring node.

  And after executing the action we will process locally $w^l$ tasks.

- **Transition Function, P**: This is the unknown transition function.
- **Reward Function, R**: The objective is to obtain an optimal offloading so that we maximize a utility function,
  U(s, a) and minimize the processing delay, D(s, a) and the overload probability, O(s, a). Therefore the reward
  for action a in state s is given by:
  $$ R(s, a) = U(s, a) − (D(s, a) + O(s, a)) $$
    - The Utility U(s, a) is given by: $U(s, a) = r_u * log(1 + w^l + w^o)$ and represents the amount of tasks ”solved” by taking the action $a$ on state $s$. $r_u$ is a utility reward.
    - The Delay function has three components given by: $D(s, a) = \chi_d\frac{t^w + t^c + t^e}{w^l + w^o}$
      - $t^w$, average time a task will be sitting in the queue of the fog node $n^l$. The expression to compute $t^w$
      is $t^w = \frac{Q^l}{\mu^l}* \mathbb{1}(w^l \neq 0) + (\frac{Q^l}{\mu^l} + \frac{Q^o}{\mu^o})* \mathbb{1}(w^o \neq 0)$.
      where $\mu^i$ is the s the computing service rate of node $n^i$, I interpreted this as the service rates on queue theory, tasks processed/unit of time.
      - $t^c$ is the communication cost in time of offloading $w^o$ tasks. The expression to compute is $t^c = \frac{2 * T * w^o}{r_{l,o}}$, where
      T is the data size of the tasks to be offloaded from $n^l$ to $n^o$, $r_{l,o}$ is the is the transmission service rate between the nodes, given by
      $r_{l,o} = B*\log(1 + \frac{g_{i,j}*P_{t_x, i}}{B*N_0})$ in this expression B is the bandwidth of the channel between the nodes and $d_{i,j}$ is the distance of the nodes
      and $\Beta_1$ and $\Beta_2$ are the ath loss constant and path loss exponent, respectively, $P_{t_x,i}$ is the power of the nodes transciever, and $N_0$ is a constant
      representing the noise power spectral density.
      - $t^e$ is the last element of the Delay function and represents the execution cost of the tasks on the nodes they will be executed on.
      The expression for this term is $t^e = \frac{I * CPI * w^l}{f^l} + \frac{I * CPI * w^l}{f^l}$, where I is the average number of instructions per task, 
      CPI is the average  number of cycles per instruction in the 
      - 
      - CPU of the nodes, and $f^i$ is the frequency of said CPUs,  we also consider that we may 
      be dealing with multi-core machines and in $f^i$ we also consider the number of cores on the CPU ($f^i = frequency * Number_of_cores$).
- The last element being considereed in the reward function is the probability of overloading of one of the nodes involved in the offloading. 
  The expression for this is $O(s,a) = \chi_o * \frac{w^l * P_{overload, l} + w^o * P_{overload, o}}{w^l + w^o}$, where $\chi_o$ is a cosntant factor representing the weight of overloading,
  and $P_{overload, l} =  \frac{max(0, \lamba_i - (Q_{i, max} - Q'_i))}{\lamba_i }$, where $\lamba_i$ is the task arrival rate of the poisson proccess governing task arrival
  $Q'_i$ is the state of the queue at node i after taking action a ($Q'_i = min(max(0 - Q_i - \bar{\mu^i}) + w^i, Q_{i, max})$, this means that the next queue state considers the number of tasks proccessed and is capped by the maximum number of tasks in the queue.)
<a name="NetworkTopology"></a>

### The Network Topology
The network we use is static and defined when the simulation is created. The nodes will be able to communicate with any
node within a circle around it which we call its neighbourhood, the size of the neighbourhood is defined in the configurations.

The nodes can't communicate with any nodes outside its neighbourhood. Although we allow direct communication to the clients.

TODO: Confirm if this behaviour is acceptable, otherwise, I'll implement a multi-hop mechanism.

### REST API
To allow receiving instructions from the model in python we wrap the simulator in a simple REST API built on top of a Spring Server. The API has provides
two endpoints:

- GET: http://localhost:8080/state - This endpoint will return the list of states with the state of every controller in the simulation at request time. 
- POST: http://localhost:8080/action -  This endpoint will send a list of the offloading instructions  each of the agents picked to the simulator.
  and returns a set of information complementary to the state, for purposes of computing the reward.

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

# Option 1: Passing a Dictionary with the key as the property being configured and the value a string with the value of the property.
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

- **The flag that manages the existence of the Cloud** This flag can take values of either 1 or 0. If the flag is set to 1 an extra node not included in the nodes specified from 'SIZE' is created. This means that if 'SIZE 10' and 'CLUD_EXISTS 1' then there is a total of 11 nodes.
    ```
    CLOUD_EXISTS 1
    ```

<a name="ConfiguringTheController"></a>
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

<a name="Developing"></a>
# Developing the Simulation
<a name="SetupTheSimulator"></a>
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


