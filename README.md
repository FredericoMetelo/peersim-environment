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

After creating the environment you need to activate it. This step may need to be repeated everytime you open the project
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
## Configuratons of the Model
A list of possible configurations is as follows, add this lines anywhere in the configuration file:
<a name="ConfiguringTheSimulation"></a>
### Configuration of the Simulation
Note: Entries without the format <protocol|init|control>.string_id.parameter_name are just constant values that help define other variables.

- **Size of the Network** defines the number of nodes in the simulation,
  for 'SIZE' nodes there are 1 Controller Node, 1 Client Node and SIZE-1 Worker Nodes. The actual variable that sets the simulation size is
  'network.size'.
  ```
  SIZE 10
  ...
  network.size SIZE
  ```

- **Number of times simulation is executed** The constant 'CYCLES' defines the total number of complete cycles (ticks in the simulation) until the simulation ends. We should note the actual variable that sets this value is 'simulation.endtime'
  The constant 'CYCLE' is used to define the number of ticks to reschedule the running of an event (IE client sending messages, worker ticking the clock once), I recommend leaving this value at one.
  ```
  CYCLES 1000
  CYCLE 1
  
  # You only need to set CYCLE and CYCLES, not recomended to alter the settings 
  # bellow directly. They are shown for informative purposes only.
  ...
  simulation.endtime CYCLE*CYCLES
  ...
  protocol.wrk.step CYCLE
  ...
  
  ```
- **Bounds of the delay a message can have** Setting MINDELAY and MAXDELAY will allow messages to possibly be delayd when being delivered.
  The delay will be such that MINDELAY <= delay <= MAXDELAY (MAXDELAY == MINDELAY == 0 means no delay, also MINDELAY<=MAXDELAY).
  ```
  MINDELAY 0
  MAXDELAY 0
  ```
- **Probability of a package/message being lost** Allows messages to be lost, the simulation for now assumes that the communications are reliable although possibly delayed therefore the environment is still not prepared to deal with this.
  ```
  DROP 0
  ```
<a name="ConfiguringTheController"></a>
### Configurations of the DiscreteTimeStepManager and PettingZoo Environemnt
- **Utility Reward, r_u**, a parameter of the reward function acts as a weight in the expression that computes the utility of a reward.
  This parameter recieves an int, the default value is 1. It is directly used by the Environment.
    ```
    protocol.mng.r_u 1
    ```
- **Delay Weight, X_d**, a parameter of the reward function acts as a weight in the expression that computes the cost associated with the delay induced by taking an action.
  This parameter recieves an int, the default value is 1. It is directly used by the Environment.
    ``` 
    protocol.mng.X_d 1
    ```
- **Overload Weight, X_o**, a parameter of the reward function acts as a weight in the expression that computes the cost associated with a node overloading (overloading of a node happens when the node has too many tasks assigned, and starts losing tasks) induced by taking an action.
  This parameter recieves an int, the default value is 1. It is directly used by the Environment.
    ``` 
    protocol.mng.X_o 150
    ```
- **Cycles between Stops**, a parameter that defines the number of time-steps it takes for the simulation to stop and await an 
    action from the agents.
    ```
    protocol.mng.cycle 5
    ``` 
<a name="ConfiguringTheClient"></a>
### Configurations of the Client
The clients have multiple DAG types that can be selected when creating a task, and there can also be multiple options for 
each task on the DAG. For each of the DAG or task types all the configurations must be specified, otherwise an error is thrown on environment creation.

#### Global to Client
- **Task Arrival Rate per Client**. This parameter is only specified once and is global to the client. At each time step a Client will with probability of taskArrivalRate send a task for each node.
    ```
    protocol.clt.taskArrivalRate 0.01
    ```

#### Task Parameters
- **Number of Tasks**, this parameter specifies the total number of task types on the simulation.
    ``` 
    protocol.clt.numberOfTasks: 2
    ```
- **Ratios of each task type, taskWeights**, the ratio of each task type must be separated by a ',' without any spaces.
The parameter represents the probability of a generated task being of each of the types. The values of each task type
will be divided by the total of the weights total to compute the ratios of generating each of the tasks.
  ```
  protocol.clt.weight 4,6
  ```
- **Average Number of Cycles in a Instruction, CPI**, the number of cycles per instruction of each task type must be separated by a ',' without any spaces. This parameter is used in two ways:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It considered in computing the time it takes for a simulation to finish a task.
    ```
    protocol.clt.CPI 1,1
    ```
- **Byte Size of Task, T**, the byte sizes of a task of each task type must be separated by a ',' without any spaces. This parameter is measured in Mbytes and used in computing the communication cost in time of offloading tasks in the Reward function.
    ``` 
    protocol.clt.T 150,100
    ```
- **Number of Instructions per Task, B**, the byte sizes of a task of each task type must be separated by a ',' without any spaces. This parameter, same as CPI, is used in two ways:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It considered in computing the time it takes for a simulation to finish a task.
  ``` 
  protocol.clt.I 200e6,250e6
  ```

A simulation started on the definitions of the examples, would have two task types:
```
1st type: {CPI:1, T:150, I:200e6} -> A task will be generated with type 1 40% of the time (4/(4+6))
2nd type: {CPI:1, T:100, I:250e6} -> A task will be generated with type 2 60% of the time (6/(4+6))
```
  
#### DAG Parameters 
 **TODO**: Specify restrictions on DAGs
- **Number of DAGs**, this parameter specifies the total number of DAG types on the simulation.
    ``` 
    protocol.clt.numberOfDAG: 2
    ```
- **Ratios of each DAG type**, the ratio of each DAG type must be separated by a ',' without any spaces.
  The parameter represents the probability of a generated DAG being of each of the types. The values of each DAG type
  will be divided by the total of the weights total to compute the ratios of generating each of the tasks.
  ```
  protocol.clt.dagWeights 4,6
  ```
- **Edges**, this parameter specifies the edge configurations of each of the DAG types.
    an edge is represented by a string `predecessorVertice successorVertice` and different edges are separated by a ','. 
The different DAGs edges are separated by a ';'.
    ```
    protocol.clt.edges 0 1,1 2,1 3,2 4,3 4,4 5;0 1,1 2,1 3,1 7,2 4,3 4,7 5,4 5
    ```
- **Byte Size of Task, T**, the byte sizes of a task of each task type must be separated by a ',' without any spaces. This parameter is measured in Mbytes and used in computing the communication cost in time of offloading tasks in the Reward function.
    ``` 
    protocol.clt.T 150,100
    ```
- **Number of Instructions per Task, B**, the byte sizes of a task of each task type must be separated by a ',' without any spaces. This parameter, same as CPI, is used in two ways:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It considered in computing the time it takes for a simulation to finish a task.
  ``` 
  protocol.clt.I 200e6,250e6
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
- **Number of Cores in Worker CPU**. This parameter is used in two ways:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It considered in computing the time it takes for a simulation to finish a task.
    ``` 
    protocol.wrk.NO_CORES 4
    ```
- **Frequency of Worker CPU**. This parameter is measured in instructions/second, and is used in two ways:
    - In computing the reward function. Specifically, affects the delay function and represents the execution cost of the tasks.
    - It considered in computing the time it takes for a simulation to finish a task.
    ``` 
    protocol.wrk.FREQ 1e7
    ```
- **Maximum Queue size, Q_MAX**. This parameter is used multiple times when computing the reward function, and is used as the threshold for a node to overload and start dropping tasks.
    ``` 
    protocol.wrk.Q_MAX 10
    ```
<a name="ConfigurationsOfTheLinks"></a>
### Configuration of the links between the nodes
- **Bandwidth**. This parameter is measured in Mhz and is used in computing the communication cost in time of offloading tasks in the Reward function.
    ``` 
    protocol.props.B 2
    ```
- **Path Loss Constant**. This parameter is used in computing the communication cost in time of offloading tasks in the Reward function.
    ``` 
    protocol.props.Beta1 0.001
    ```
- **Path Loss Exponent**. This parameter is used in computing the communication cost in time of offloading tasks in the Reward function.
    ``` 
    protocol.props.Beta2 4
    ```
- **Transmission Power**. This parameter is measured in dbm and is used in computing the communication cost in time of offloading tasks in the Reward function.
    ``` 
    protocol.props.P_ti 20
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

This will package a jar with all necessary dependencies to run the simulator. The jar can be found on the `target/peersim-srv-0.0.1-SNAPSHOT.jar`
directory/folder.


