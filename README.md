# [Peersim-Env](https://arxiv.org/abs/2403.17637v2)

PeersimGym, an environment to train task-offloading MARL algorithms on Edge network simulations. The simulation behind the environment is built using the [Peersim Peer-to-Peer simulator tool](https://peersim.sourceforge.net/) that we adapted to simulate Edge Systems, and an API following the [PettingZoo Framework](https://pettingzoo.farama.org/) is provided in _Python_.

This repository contains the implementation of Peersim-env. This is composed of two parts. A Gym environment python class, and
a server that wraps the simultion of the peersim environment in a REST API allowing the passing of information to the python Gym environment.

We provide some agent implementations to act as examples of agents that work on our repository in a separate [repository](https://github.com/FredericoMetelo/TaskOffloadingAgentLibrary/).

# Index
1.[How The Simulation Works](##HowTheSimulationWorks)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1.1.[The Simulation Server](###TheSimulationServer)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1.2.[MDP](###MDP)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1.3.[Network Topology](###NetworkTopology)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1.4.[REST API](###RESTAPI)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1.5.[Implementing your own protocols for the Simulation](###CustomizingSim)<br>
2.[Quickstart](#Quickstart)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.1.[Setting Up](##SettingUp)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.1.1.[External Tools](###ExternalTools)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.1.2.[Setup Anaconda](###SetupAnaconda)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.[Utilizing the Environmnet](##Utilizing)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.1.[Utilization Example](###UsingTheEnvironment)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.2.[Configurations](##Configurations)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.3.[Configuring The Simulation](###ConfiguringTheSimulation)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.4.[Configuring The Controller](###ConfiguringTheController)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.5.[Configuring The Client](###ConfiguringTheClient)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.6.[Configuring The Workers](###ConfigurationOfTheWorker)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.2.7.[Configuring The Links](###ConfigurationsOfTheLinks)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.3.[Developing the Simulation](##Developing)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.3.1.[Setup The Simulator](###SetupTheSimulator)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.3.2.[Maven Dependencies](###MavenDependencies)<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2.3.3.[Compiling The Simulator](###CompilingTheSimulator)<br>
3. [Bibtex](###Bibtex)<br>

## Changelog
- Revamped the Cloud to be more logical to use.
- Fixed a bug regarding the usage of the cloud, when some nodes knew all the other nodes in the network, and others didn't.
- Added a simple visualization of the Cloud whenever the cloud is active.
- Added a new configuration to the simulation that allows for the setting of the Cloud Position.
- Tweaked the definition of the cloud properties so that the number of VMs and the default power of the VMs have separate parameters (For now all VMs will have the same processing power).

<a name="HowTheSimulationWorks"></a>
## How the Simulation Works
<a name="TheSimulationServer"></a>
### The Simulation Model
 
The structure of the simulator (assumes a set of client devices $\mathcal{C}$, generating data to be processed, similar to sensors in IoT devices. This data is then sent to a worker node, which can either process the task locally or offload it to other nodes with available resources. Each device $w_i$, in a set of worker nodes $\mathcal{W}$, is characterized by the following features, which can be included in the system or neglected:


-  **Task queue**, $Q_i$, a structure that allows at most $Q_i^{\text{max}}$ received tasks to be stored and await to be processed in a first-in-first-out fashion. Any tasks received above the capacity of the node will be dropped.
-  **Number of processors**, $N_{\phi}^i$, of frequency, $\phi_i$, in $MHz$. Therefore, the node can process $N_{\phi}^i\phi_i$ instructions per time step. Tasks are processed sequentially, and all the available cores are utilized when processing a single task. Upon completion, the node will send the result to the origin ---  either the client that generated the task, or to other node from where the task was received, which will repeat the process until the client receives the results.
-  **Transmission power**, $P_i$, that affects the communication delays.
-  **Position**, $l_i$, which also affects the communication delay and other proximity-based mechanisms.
 


The set of workers $\mathcal{W}$ can be divided into tiers/layers. All nodes within a tier are generated with similar resource specs. The tier/layer system is useful for modeling hierarchical networks like in the Fog Computing paradigm. Optionally, a user can include a cloud server in the system. The cloud does not have a maximum queue capacity and consists of multiple virtual machines (VM) that are assigned to tasks in the order they arrive.
Hence, the inclusion or exclusion of tiers or clouds allows one to cover a wide range of structures, from a decentralized peer-to-peer (P2P) to a hierarchical n-tier network.

A set of the worker nodes in the network hosts a Controller protocol -- which allows that worker to offload tasks to its neighbors. The responsibilities of the controller protocol can be summed up by 1.) maintaining information on the state of the node’s neighboring workers, and 2.) making offloading decisions.

**Simulation Components**<br>
The simulation works by using the Peersim simulation tool as its base, which offers two engines an event-driven and a cycle-driven engine, although the cycle-driven engine can be imitated by scheduling an event at each time step. The tool works by implementing a network as a list of nodes that run a group of user-specified protocols, which for PeersimGym's simulator are the client, worker, and controller protocols and a special Simulation Manager protocol;
The Simulation Manager Protocol will at a configurable interval stop the simulation and await a request with an action to be posted to the _Spring Boot_ server. It will then distribute the actions described in the post actions request and resume the simulation until the interval to stop has passed again.

The other protocols at each time step execute a cyclical predefined behavior:

- For the worker protocol, this behavior consists of processing the first available task in the queue, by updating a counter of instructions for that task. Whenever a task is finished, the result is sent back to the client that generated it, using multiple hops if the task was offloaded multiple times, and the next available task is selected or if the queue is empty the worker becomes idle, remaining that way until another task arrives.
- For the client protocol, this behavior consists of deciding whether to send a task to each of the workers in their neighborhood that can receive tasks based on a Poisson process. The worker's that can receive tasks are defined through the configurations.
- For the controller protocol, this behavior consists of checking if the worker has had any changes in its internal state. If it did then a one-hop broadcast to its neighborhood with the updated state information.


For accurate simulation, we also utilize events in our simulation, each protocol will initiate a routine for dealing with different events:

- The worker protocol
  - Receive Task from an offload event, the worker is responsible for receiving a task from another node, and if there is available space add the task to be processed in the node, otherwise the task is dropped.
  - Receive a new Task event, the worker is responsible for receiving a task directly from a client, and if there is available space add the task to be processed in the node, otherwise the task is dropped.
  - Receive a concluded task's result event, the worker is responsible for redirecting the task towards the client that generated it, wither by offloading to a closer worker, or by sending it back to the client if it is in the neighborhood.

- The Client protocol:
  - Task completed event, the client is responsible for handling a task completion, and registering the required metrics.

- The Controller protocol
  - Neighbour State update event, the controller is responsible for updating the node's information on the state of the neighbor that emitted the event.


In the case of the controller protocol, the offloading event is different from the other events. The instructions are received through the REST request from the simulation and are passed by a special class evoking a method directly on each of the controllers which will take the actions immediately


**Communication between the agent and the environment.**<br>
As the clients generate tasks and the workers receive them, nodes with a _controller protocol_ might decide to offload tasks instead of processing them locally, and the controller protocols will share their local information through the one-hop broadcasts. All the communication in the network, i.e., offloading of the tasks between the nodes, is assumed wireless. To compute the number of bits sent per second, we consider the _Shannon-Hartley_ theorem, where the latency to communicate $\alpha$ bits from node $w_i$ to  $w_j$ is
$$
T^{\text{comm}}_{i,j}(\alpha) =\frac{\alpha}{B_{i,j} \cdot \text{log}(1 + 10^{\frac{P_i + G_{i,j} - \omega_0}{10}})},
$$

where $B_{i,j}$ is the channel bandwidth, $G_{i,j}$ is the channel gain, $P_i$ is the power of the node's transceiver, $\omega_o$ and $\eta$ are the noise power and a constant representing the noise power spectral density respectively, s.t. $\omega_0 =  \eta + 10 \log_{10}(B_{i,j})$.

**Task Model**<br>
All the workload in the simulation comes in the form of the processing demands of the tasks. A task $\tau_i$ is a computational requirement generated by a client and is represented as a tuple  $\tau_i = \langle i, \rho_i, \alpha_i^{\text{in}}, \alpha_i^{\text{out}}, \xi_i, \delta_i \rangle$, where
- $i$ is a unique identifier of the task;
- $\rho_i$ is the number of instructions to be processed;
- $\alpha_i^{\text{in}}$ is the total data size of the input;
- $\alpha_i^{\text{out}}$ is the data size of the output/results;
- $\xi_i$ is the cost in CPU cycles per instruction;
- $\delta_i$ is the deadline of the task, i.e., the maximum allowed latency for returning the results (see Tab.~\ref{tab:notation}).

The time is logically divided into discrete time slots of a unit size $t=1,..., T$. Tasks are generated following a \textit{Poisson} distribution with the arrival rate $\lambda>0$.
Clients deliver their generated tasks to nearby worker nodes, who are responsible for processing them (see Fig.~\ref{fig:FullDiagram}). Received tasks are stored in the queue, awaiting for the processing resources to become available, unless the queue is already full, in which case the task is dropped.

<a name="MDP"></a>
### MDP

To be applied to Reinforcement Learning the problem needed to be specified as an Markov Game.
In our setting, in a network of $N$ nodes, an MG is represented as a tuple $\langle n, \mathcal{S}, \mathcal{A}, P, R, \gamma \rangle$, where $n$ is the number of agents (i.e., nodes with a controller protocol), $\mathcal{S}$ is the state space, $\mathcal{A}$ is the action space, $P$ is an (unknown) transition probability function, $R$ is the reward function, and $\gamma$ is a reward discount factor. In the following, we introduce these constructs in more detail.

- **State Space**: Different Edge System settings will have different definitions of state and action space, and shaping the reward will decide what are the important and less important objectives for the agent. In this paper, we consider a wide set of state spaces that can be customized by excluding or combining specific options to model the system of interest. At time step $t$, each node will broadcast its local state to each of its neighbors and receive their local states to build a local state representation before deciding on the action. The state space is represented by a tuple $\textit{S}=(\mathcal{I}, \mathcal{K}, \mathcal{Q}^t,\mathcal{F}, \mathcal{L},\{\mathcal{B}_1,...,\mathcal{B}_n\}, \{\mathcal{P}_1,...,\mathcal{P}_n\})$, where
    -  $\mathcal{I}=\{1,...,n\}$ is an array with the IDs of all the nodes in the network. All the following arrays respect the ordering of the IDs in this array;
    -  $\mathcal{K}=\{\kappa_1,...,\kappa_n\}$ is an array specifying the layer/tier for each node;
    -  $\mathcal{Q}^t=\{Q^t_1,..., Q^t_n\}$ is an array storing the queue size for each node in the network, at time step $t$;
    -  $\mathcal Q_i^{\text{max}}=\{Q_1^{\text{max}},..., Q_n^{\text{max}}\}$ is an array storing the maximum capacity of the queues for each node in the network;
    -  $\mathcal{N}_n$, which is an array with the ids of the nodes that belong to node $n$'s neighborhood.
    -  $\mathcal{F}=\{N_{\phi}^1\phi_1,...,N_{\phi}^n\phi_n\}$ is an array storing the processing power for each node in the network;
    -  $\mathcal{L}=\{l_1,...,l_n\}$ is an array storing the position for each node in the network;
    -  $\mathcal{B}_i=\{B_{i,1},...,B_{i,n}\}$ is an array storing the bandwidth of the channels for node $w_i$ to all its neighbors;
    -  $P_i$ is the transmission power of  node $w_i$’s antenna.

- **Action Space**: Action Space, $A$, corresponds to the output layer of a DRL agent (see Fig.~\ref{fig:StateAction}). The action $a_t\in\{1,...,N\}$ represents the index of the destination node, which might be one of the neighboring workers or the observed node itself, in case it decides to process the task locally. We use $a_t$ interchangeably to denote both the index of the destination node and the act of sending a task to that node, as long as clarity is maintained within the context.

- **Transition Function**: This is the unknown transition function.
- **Reward Function**: A reward function guides an RL agent when choosing the action to perform, and it might have a different structure even when the system model does not change, depending on the desired objective. In this paper, we will adopt a reward function as defined in~\cite{baek2019managing}, i.e., a reward function for agent $w_i$, $R_i$, is structured to maximize the utility, $U_i(s_t, a_t)$, and minimize the total delay, $D_i(s_t, a_t)$, and the overloading cost, $O_i(s_t, a_t)$. In particular, the reward for the action $a_t$ in state $s_t$, received by an agent $w_i$ by offloading (or not) task $\tau_k$, is given by
  $$
  \label{eq:rwrdFunciton}
  R_i(s_t, a_t) = r_u - (D_i(s_t, a_t) + \chi_OO_i(s_t, a_t)),
  $$ where $r_u$ is a utility reward and represents the gain over completed tasks. Each term of the reward function is explained in detail below. Let $I_i(a_t)$ be an indicator function, such that we will have $I_i(a_t)=1$ iff the task is left for local computing on node $w_i$, and $I_j(a_t)=1$ iff it is offloaded to a neighboring node $w_j$, with $j\neq i$.
 
  -  Delay function,
  $$
  D_i(s_t, a_t) = \chi_D^{\text{wait}} T_{i,a_t}^{\text{wait}}(\tau_k) + \chi_D^{\text{comm}} T_{i,a_t}^{\text{comm}}(\alpha_k^{\text{out}}) + \chi_D^{\text{exc}} T_{i,a_t}^{\text{exc}}(\tau_k),
  $$
  is a weighted sum of three time-related terms, with hyperparameters $\chi_D^{\text{wait}}$, $\chi_D^{\text{comm}}, \chi_D^{\text{exc}} \geq 0$; namely,
  $T_{i,a_t}^{\text{wait}}(\tau_k) = \frac{Q_i^t}{N^i_{\phi}\phi_i} + \sum_{j\neq i}\frac{Q_j}{N^j_{\phi}\phi_j} I_j(a_t)$ is the time task $\tau_k$ will be waiting in the queue of node $w_i$ (and $w_j$ in case it is offloaded), where $\phi_i$ is the computing service rate of node $w_i$, and $N^i_{\phi}$ is the number of processors it has.
  $T_{i,a_t}^{\text{comm}}(\alpha_k^{\text{out}})$ is the communication cost of task offloading, expressed as a delay, as defined in (\ref{eq:time_communication}). Note that if the task is executed locally, this term is zero.
  $T_{i,a_t}^{\text{exc}}(\tau_k)= \frac{t\rho_k \xi_k}{N_{\phi}^{a_t}\phi_{a_t}} - \frac{t\rho_k \xi_k}{N_{\phi}^{i}\phi_{i}}$ represents the difference in execution cost of the tasks between processing the task locally compared to processing it in the target node. Here, $\rho_k$ is the number of instructions per task, and $\xi_k$ is the number of CPU cycles per instruction.
  -  Cost of overloading,
  $O_i(s_t, a_t) = -\log (p_t^{Oa_t})/3$,
  is weighted by the parameter $\chi_O\geq 0$, where
  $p_t^{O{a_t}} =  max(0, \frac{Q_{a_t}^{\text{max}} - Q_{a_t}}{Q_{a_t}^{\text{max}}})$, represents the distance to overloading node $w_i$, and
  $Q'_{a_t} = min( max(0, Q_{a_t} - \phi_{a_t}) + 1, Q_{a_t}^{\text{max}})$ is the expected state of the queue at node $w_{a_t}$, after taking action $a_t$.
  - $\gamma$ is a reward discount factor.

<a name="NetworkTopology"></a>

### The Network Topology
The network we use is static and defined when the simulation is created. The nodes will be able to communicate with any
node within a circle around it which we call its neighbourhood, the size of the neighbourhood is defined in the configurations.

The nodes can't communicate with any nodes outside its neighbourhood.

### REST API
To allow receiving instructions from the model in python we wrap the simulator in a simple REST API built on top of a Spring Server. The API has provides
two endpoints:

- GET: http://localhost:8080/state - This endpoint will return the list of states with the state of every controller in the simulation at request time. 
- POST: http://localhost:8080/action -  This endpoint will send a list of the offloading instructions  each of the agents picked to the simulator.
  and returns a set of information complementary to the state, for purposes of computing the reward.

<a name="CustomizingSim"></a>
### Implementing your own protocols for the Simulation
PeersimGym's simulator allows for in-depth customization by changing the very protocols that model the workers, clients, and controllers.

To implement your own protocol you need to create new classes that extends the `PeersimSimulator.peersim.env.Nodes.Workers.AbstractWorker`, `PeersimSimulator.peersim.env.Nodes.Controllers.AbstractController` or `PeersimSimulator.peersim.env.Nodes.Clients.AbstractClient` classes. See the classes in question for the methods that need to be implemented, we provide detailed descriptions of each in the Javadocs.

Note: In-depth documentation is still a work in progress, so not all methods are properly documented yet.

Furthermore, it is also possible to add action types by implementing the `PeersimSimulator.peersim.env.Records.Actions.Action` interface, and it's possible to retrieve different information from the network by implementing different `PeersimSimulator.peersim.env.Records.SimulationData.SimulationData`, note that this would reuire implementing Controller and Worker protocols that are capable of dealing with the new types. When implementing new action types or simulation datas, the new classes will need to be registered in the respective interface to be able to marshal them with jackson, see the interfaces in question for examples.

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
This environment is missing the PeersimGym module, which needs to be installed to use the environment in other projects. Furthermore, a different set of reuirements is used in windows due to unavaliable packages.

**Linux based systems**
```
conda env create -f Setup/environement.yml
```

**Windows**
```
conda env create -f Setup\requirements_win.yaml
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
### Utilization Example
To start the simulation all you need to do is create a PeersimEnv object in your python code. This environment can then be used 
like a regular PettingZoo environment. 

```python
import peersim_gym.envs.PeersimEnv import PeersimEnv

env = PeersimEnv(configs={...})
actions = {agent: env.action_space(agent).sample() for agent in env.agents}
observations, rewards, terminations, truncations, infos = env.step(actions)
```

### Configuring the Environment
It is possible to have a custom configuration for the Peersim Simulator. To configure the environment set the configs parameter on the Peersim Environment Constructor to wither the path of the file with the configs (See the `src/peersim-gym/peersim_gym/envs/configs/config-SDN-BASE.txt` for the base file, and the config file must always include the Simulation Definitions, which are not set on the base config file), or pass a dictionary with the key the config name and the value wanted (as a string).
The following code illustrates the different ways of configuring the environment.
```python
from peersim_gym.envs.PeersimEnv import PeersimEnv
# There are 3 options to configure the environment.

# Option 1 (Recomended): Passing a Dictionary with the key as the property being configured and the value a string with the value of the property.
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

For ease of usage we provide a set of utilities for generating the config file in the `peersim_gym.ConfigHelper`. The config helper can be used like:
```python
from peersim_gym.ConfigHelper import ConfigHelper

config_dict = ch.generate_config_dict(...)

env = PeersimEnv(configs=config_dict)
```

The environment is highly customizable a full list with detailed descriptions of the possible configurations can be found in the simulator repository, to facilitate the configuration process we provide a configuration helper in the `src/EnvUtils/ConfigHelper.py`. The helper can be used to generate a configuration dictionary that can be used to create the environment.
The helper generates a configurationn dictionary that can then be passed to the environment creation function. The helper can be used as follows:
```python
import peersim_gym.ConfigHelper.ConfigHelper as ch

config_dict = ch.generate_config_dict(controllers="[0]",
                         size=10,
                         simulation_time=1000,
                         radius=50,
                         frequency_of_action=2,

                         has_cloud=1,
                         cloud_VM_processing_power=[1e8],

                         nodes_per_layer=[10],
                         cloud_access=[0],
                         freqs_per_layer=[1e7],
                         no_cores_per_layer=[4],
                         q_max_per_layer=[50],
                         variations_per_layer=[0],
                         layersThatGetTasks=[0],

                         task_probs=[1],
                         task_sizes=[38],
                         task_instr=[4e7],
                         task_CPI=[1],
                         task_deadlines=[100],
                         lambda_task_arrival_rate=0.5,
                         target_time_for_occupancy=0.5,

                         comm_B=2,
                         comm_Beta1=0.001,
                         comm_Beta2=4,
                         comm_Power=20,

                         weight_utility=10,
                         weight_delay=1,
                         weight_overload=150,
                         RANDOMIZETOPOLOGY=True,
                         RANDOMIZEPOSITIONS=True,
                         POSITIONS="18.55895350495783,17.02475796027715;47.56499372388999,57.28732691557995;5.366872150976409,43.28729893321355;17.488160666668694,29.422819514162434;81.56549175388358,53.14564532018814;85.15660881172089,74.47408014762478;18.438454887921974,44.310130148722195;72.04311826903107,62.06952644109185;25.60125368295145,15.54795598202745;17.543669122835837,70.7258178169151",
                         TOPOLOGY="0,1,2,3,6,8;1,0,2,3,4,5,6,7,8,9;2,0,1,3,6,8,9;3,0,1,2,6,8,9;4,1,5,7;5,1,4,7;6,0,1,2,3,8,9;7,1,4,5;8,0,1,2,3,6;9,1,2,3,6",
                         MANUAL_CONFIG=False,
                         MANUAL_CORES="1",
                         MANUAL_FREQS="1e7",
                         MANUAL_QMAX="10",
                         clientLayers="0",
                         defaultCPUWorkload="2.4e+9",
                         defaultMemoryWorkload="100",
                         workloadPath=None,
                         clientIsSelf=1
                         )
env = PeersimEnv(configs=config_dict, ...)

```
### Configuration Options
There are many ways of using the simulator tool, we will leave the details on what each variable does for the simulator repository, where we go in detail on each one. But we will list here the variables required to activate each of the modes, note that with this values we would be using the default values adn more information on how to use the plausible configurations can be found in the
following sections.

We separate the main configurations in the topology options and the task options, options for each category can be picked interchangeably (I.E. it's possible to select the ether topology and the random task generation). The main configurations are:
- **Topology Configuration**: The configuration of the topology of the network.
  - **Random Topology**: The configuration of the random topology that will be generated. The variables required to use this mode are:
      ```python
      RANDOMIZETOPOLOGY=True,
      RANDOMIZEPOSITIONS=True,
      size=<NUMBER OF NODES>,
    ```
  - **Manual Topology**: The configuration of the manual topology that will be used.
    ```python
    RANDOMIZETOPOLOGY=False,
    RANDOMIZEPOSITIONS=False,
    POSITIONS=[<POSITIONS>],
    TOPOLOGY=[<TOPOLOGY>],
    ```
  - **Ether Topology**: The configuration of the ether topology that will be used.
    ```python
    RANDOMIZETOPOLOGY=False,
    RANDOMIZEPOSITIONS=False,
    MANUAL_CONFIG=True,
    MANUAL_CORES=[<CORES>],
    MANUAL_FREQS=[<FREQUENCIES>],
    MANUAL_QMAX=[<QMAX>],
    POSITIONS=[<POSITIONS>],
    TOPOLOGY=[<TOPOLOGY>],
    
    ```
- **Task Configuration**: The configuration of the tasks that will be generated in the simulation. The task generation does not require special configurations, but for the simulaiton mode to be set, which we will show how to do in following sections.
  - **Random Task Generation**: The configuration of the random task generation that will be used.
  - **Trace Task Generation**: The configuration of the trace task generation that will be used. Requires a special simulaiton type to be set.


### Task generation configurations
#### Selecting the task generation mode
There are two modes for task generation, one based on the trace-generation tool and another that uses a set of potential
user defined tasks. To change between the different modes set the environment to be created with one of two options:
```python

config_dict = ch.generate_config_dict(..., 
                                      workloadPath="<PATH TO TRACE DATASET FILE>",
                                      ...)

simtype = "basic" # Uses the user defined workload generation
simtype = "basic-workload" # Uses the trace generation tool

...
env = PeersimEnv(configs=config_dict, ..., simtype=simtype, ...) 
```

Notice: that you must set the configuration indicating what is the file containing the required dataset. We recommend using the configuration helper to generate the configuration dictionary, for manually generating a file see the section `Generating your own workload from the trace generation tool`.
#### Generating your own workload from the trace generation tool

> This instructions require the repository for the PeersimEnv Simulator, that can be found in the simulator repository.

If you wish to generate a dataset of a different size or characteristics than the one provided, you can use the trace generation tool to generate a new dataset.

Set up the trace generation tool following the instructions in the [Trace Generation repository](https://github.com/All-less/trace-generator.git). We only require the file generated by the tool, hence we recommend following the `pip3` installation.
After installig, follow the repo's instructions on generating the datasets, move the datasets generated to the `/Datasets/` directory in the simulator repository.

We then convert the tool to be usable with PeerSim by running the script from the `src/EnvUtils/AlibabaTraceGeneratorCleaner.py` in the simulator repository. This will create the file `Datasets/alibaba_trace_cleaned.json` that can be used in the environment.

#### Selecting the schedulling
There are two options of schedulling in the peersimgym environment, constant task arrival rate and utilizing a schedulling generation function.
By default the constant task arrival rate will be used (with the default or specified value), but a schedulling can be passed by passing a schedulling function to the configs. The config helper can be used to manage the creation of the correct schedulling.
```python
# The first option with a fixed schedulling can be obtained with:
task_arrival_rate=1 # any other float or integer value
schedule, _lambda = ch.get_lambda_and_schedule(task_arrival_rate)

# There are two ways of passing a schedulling.
## Using the built in schedullings:
schedule = "Linear" # any of the available schedullings
schedule, _lambda = ch.get_lambda_and_schedule(task_arrival_rate)

## Using a custom schedulling (see bellow how to create a custom schedulling):
def custom_schedule(total_time, no_schedules):
    ...
    return schedule

schedule = custom_schedule # Pass the funtion to the config generator.
_lambda = ... # Any number, not used.

config_dict = ch.generate_config_dict(...,
                                      lambda_task_arrival_rate=_lambda,
                                      schedule_task_arrival_rate=schedule,
                                      ...
                                      )
```




We provide a set of built in schedullings that are available in `src/peersim-gym/peersim_gym/ConfigHelper/ConfigHelper.py`.
The available schedullings are:
- **Linear**: Generates a linear schedule where the task arrival rate increases linearly from 1 to 10 over the total time.
- **Zigzag**: Generates a zigzag schedule where the task arrival rate increases linearly from 1 to 10 and then decreases back to 1, repeating this cycle.
- **Uniform**: Generates a random schedule where the task arrival rate is randomly selected between 1 and 10 every 100 time units.
- **HeteroUniform**: Generates multiple random schedules where each schedule has a task arrival rate randomly selected between 1 and 10 every 100 time units.
- **HeteroZigzag**: Generates multiple zigzag schedules where each schedule has a task arrival rate that increases linearly from 1 to 10 and then decreases back to 1, repeating this cycle.

#### Scheduling Generating Function Requirements

A scheduling generating function in PeersimGym is designed to create a schedule based on the task arrival rate. The function must take in two parameters: `total_time`, which specifies the total duration of the simulation, and `no_schedules`, which determines how many schedules need to be generated. The function should return a tuple. The first element of the tuple is a string containing the schedule in a format understandable by PeersimGym, while the second element is a string representing the schedule's name.

The peersimgym understandable format is:
The format is task_arrival-no_steps_to_last;task_arrival-no_steps_to_last;.... Each node that receives tasks must have a corresponding schedule.

In terms of functionality, the function needs to generate a schedule string where each entry specifies the task arrival rate and the number of time steps it will be maintained. The schedule should be repeated `no_schedules` times. If the total time for the schedule is less than the `total_time`, the last task arrival rate should be extended until the end of the simulation.

For example, the `linearSchedule` function increases the task arrival rate linearly from 1 to 10 over the total time specified, with the rate increasing by 1 every 100 time units.

```python
def linearSchedule(total_time, no_schedules):
  """
  This function generates a linear schedule for the task arrival rate. The task arrival rate increases linearly
  from 1 to 10 over the total time specified. The rate is increased by 1 every 100 time units. The schedule is duplicated
  no_schedule times.

  :param total_time: Total time for the simulation
  :param no_schedules: Number of schedules to generate.
  :return: tuple(string, string) with the scheduled string in a Peersimgym understandable format - And the schedule name.
  """
  schedule_info = "Linear"
  max_rate = 10
  rate = 1.0
  slot_time = 100
  schedule = ""
  curr_time = 0
  while curr_time < total_time:
    schedule += f"{rate}-{slot_time}"
    curr_time += slot_time
    rate += 1
    if rate > max_rate:
      rate = max_rate
    if curr_time < total_time:
      schedule += ","

  list_of_schedules = [schedule] * no_schedules
  schedules = ""
  for schedule in list_of_schedules:
    schedules += schedule + ";"
  schedules = schedules[:-1]
  return schedules, schedule_info
```



### Specifying topologies
#### Generating an Ether topology
The process to generate teh ether topology requires using the `ether` tool. The tool is available in the [Ether repository](https://github.com/edgerun/ether.git).
To generate the topology, copy and past the contents of the folder `src/Utils/EtherTopologyGeneration` into the ether repository
root. Then, from the ether repository run the python script `examples/MyNet.py`. This will generate a file called `topology.json`
that can be used in the environment.

#### Generating the topology using the TopologyGenerator
There are a total of 5 topologies that can be generated:

- **Fully-Connected**: In this topology, every node is directly connected to every other node. This ensures the highest level of connectivity, allowing for efficient communication but also requiring the most resources.

- **Random**: Nodes are connected in a random manner, resulting in an unpredictable network structure. This topology is useful for simulating decentralized and less structured environments.

- **Grid**: Nodes are organized in a grid-like structure, where each node is connected to its immediate neighbors (up, down, left, right). This topology is commonly used in spatially distributed systems or when emulating certain physical layouts.

- **Ring**: Nodes are arranged in a circular structure, with each node connected to two neighbors—one on its left and one on its right. This topology ensures minimal redundancy while maintaining a predictable connection pattern.

- **Bridged-Clusters**: Nodes are divided into clusters (or groups), where each cluster is internally well-connected, and some nodes (bridges) connect clusters to facilitate inter-cluster communication. This topology is ideal for simulating hierarchical or modular networks.

<a name="Configurations"></a>

#### Using the generated topology in the environment
Using the generated topology in the environment requires a special set of configuration values to be set. To help with reading this values from the configuration file to the environment we provide a helper function in the `src/EnvUtils/EtherTopologyReader.py`. The function reads the topology file and returns a dictionary with the values that can be used in the configuration helper. The function can be used as follows:

```python 
import src.EnvUtils.EtherTopologyReader as etr

topology_file = "EetherTopologies/Legacy/one_cluste_8rpi_manual.json"
topology_dict = etr.get_topology_data(topology_file, project_coordinates=True, expected_task_size=32e7)

manual_config = True
manual_no_layers = topology_dict["number_of_layers"]
manual_layers_that_get_tasks = topology_dict["layers_that_get_tasks"]
manual_clientLayers = topology_dict["client_layers"]
manual_no_nodes = topology_dict["number_of_nodes"]
manual_nodes_per_layer = topology_dict["nodes_per_layer"]
manual_freqs = topology_dict["processing_powers"]
manual_freqs_array = topology_dict["freqs_per_layer_array"]
manual_qmax = topology_dict["memories"]
manual_qmax_array = topology_dict["q_max_per_layer_array"]
manual_cores = topology_dict["cores"]
manual_cores_array = topology_dict["no_cores_per_layer_array"]
manual_variations = topology_dict["variations_per_layer_array"]
manual_positions = topology_dict["positions"]
manual_topology = topology_dict["topology"]
controllers = topology_dict["controllers"]  # ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"]

config_dict = ch.generate_config_dict(...,
                                      # This variables indicate to the simulation that a manual topology configuration is being used
                                      MANUAL_CONFIG=manual_config,
                                      RANDOMIZETOPOLOGY=False,
                                      RANDOMIZEPOSITIONS=False,

                                      # This are the values extracted from the file generated with the simulation topology data
                                      size=manual_no_nodes,
                                      nodes_per_layer=manual_nodes_per_layer,
                                      freqs_per_layer=manual_freqs_array,
                                      no_cores_per_layer=manual_cores_array,
                                      q_max_per_layer=manual_qmax_array,
                                      variations_per_layer=manual_variations,

                                      MANUAL_QMAX=manual_qmax,
                                      MANUAL_CORES=manual_cores,
                                      MANUAL_FREQS=manual_freqs,

                                      layersThatGetTasks=manual_layers_that_get_tasks,
                                      clientLayers=manual_clientLayers,

                                      POSITIONS=manual_positions,
                                      TOPOLOGY=manual_topology,
                                      ...
                                      )
env = PeersimEnv(configs=config_dict, ...)

```

### Adding a reward shaping function
Reward Shaping is an useful tool to speed up the reinforcement learning process. We provide a mechanism to pass a reward shaping term to the environment.
A default function can be found in the `src/EnvUtils/RewardShaping.py` file. The function must take the state as an argument and return the reward. The function can be added to the environment by setting the phy_rs_term parameter as follows:
```python
import src.EnvUtils.RewardShaping as rs

def reward_shaping(state):
    # Add your reward shaping logic here, we assume output is stored in reward_shaping_term
    return reward_shaping_term

env = PeersimEnv(..., phy_rs_term=reward_shaping, ...)
```

### Visualizing the environment
The environment provides three visualization modes, that can be set with the render_mode parameter. The modes are:
- `None`: Minimum information is printed to stdout.
- `"ascii"`: We print extra information on the state of the simulation to stdout.
- `"human"`: We provide a visual representation of the simulation using a pygame canvas.

The visualization can be set as follows:
```python
render_mode = None # or
render_mode = "ascii" # or
render_mode = "human"

env = PeersimEnv(..., render_mode=render_mode, ...)
```

A video explaining the "human" rendering mode can be observed on the following video (also available in the repository as FinalVisualization.mp4):
https://github.com/FredericoMetelo/TaskOffloadingAgentLibrary/assets/50637681/ff10c38e-7026-4199-b662-1e424fd3d43a





## Configurations In-depth

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
- **Simulaiton Scaling** To allow a finer grained time-scale we permit a re-scaling parameter that will convert each simulation cycle into `SCALE*CYCLE`. All the time-dependent properties of the simulation except cycle are rescaled as well. At it's core, the scaling parameter allows events that would happen at the same time to be more separated in time.
  ```SCALE 10```

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
   Using the ta in the schedule instead.
    ```
    protocol.clt.taskArrivalRate 0.1
    ```
   This parameter is now deprecated in favor of specifying a schedule with constant task arrival rate (Note: automatically done by the config helper, see Quickstart)

- **Scheduling Configurations**. The PeersimGym environment allows clients to vary the task arrival rate using scheduling mechanisms using a configurable pre-determined schedule. The schedules parameter allows specifying the task arrival rate for each node that receives tasks. The format is task_arrival-no_steps_to_last;task_arrival-no_steps_to_last;.... Each node that receives tasks must have a corresponding schedule. If the schedule does not cover the entire simulation, the last task arrival rate in the schedule will be used until the end of the simulation.
  ```
  "schedules": "0.6-1000;0.8-500;1.0-2000"
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
    MANUAL_CORES 4;8
    ```
- **Ether Frequencies Default** Specifies the frequency of the CPU of each node in the ether topology. The value for each layer is separated by a ','. For example, given the value '1e7,3e7', the nodes in the layer index 0 will have a frequency of 1e7 instr/second and the nodes in the layer index 1 will have a frequency of 3e7 instr/second.
    ```
    MANUAL_FREQS 1e7;3e7
    ```
- **Ether Q Maxes Default** Specifies the maximum queue size of each node in the ether topology. The value for each layer is separated by a ','. For example, given the value '10,50', the nodes in the layer index 0 will have a maximum queue size of 10 and the nodes in the layer index 1 will have a maximum queue size of 50.
    ```
    MANUAL_QMAX 10;50
    ```

### Configuration specific to the Trace Generation integration
-**Workload File** - This parameter specifies the path to the json file that contains the workload to be used in the simulation. 
    ```
    protocol.clt.workload_file /home/user/peersim-env/configs/examples/trace.json
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

<a name="Bibtex"></a>
# Bibtex
This work is based on our paper accepted at European Conference on Machine Learning and Principles and Practice of Knowledge Discovery in Databases (ECMLPKDD 2024): https://arxiv.org/abs/2403.17637

If you find this useful, consider citing:
```
@inproceedings{metelo2024peersimgym,
      title={PeersimGym: An Environment for Solving the Task Offloading Problem with Reinforcement Learning}, 
      author={Frederico Metelo and Stevo Racković and Pedro Ákos Costa and Cláudia Soares},
      booktitle={ECML/PKDD},
      year={2024}
}
```

