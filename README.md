# Peersim-Env

This repository contains the implementation of Peersim-env. This is composed of two parts. A Gym environment python class, and
a server that wraps the simultion of the peersim environment in a REST API allowing the passing of information to the python Gym environment.


### The Simulation Server

We build a simulation based on the Network used in the paper by J.-y. Baek, G. Kaddoum, S. Garg, K. Kaur and V. Gravel, "[Managing Fog Networks using Reinforcement
Learning Based Load Balancing Algorithm](https://ieeexplore.ieee.org/document/8885745)".

We utilize [Peersim](https://peersim.sourceforge.net/) to simulate a network with three components, that are:

- Worker Nodes: Simulate the execution of tasks, offload tasks on request by the controller and communicate status messages to the controller.
- Client Nodes: Only one client that will for each node generate and request tasks following a poisson process. the Client also keeps an
  average of the time the reuqests took to be processed in the time internal to the simulator.
- Controller Node: The controller nodes keep information of the status of each node and recieve the offload instructions from the outside to pass 
  them to the relevant worker node. The worker node to be offloaded to is selected on a round robin style across all nodes.

### MDP 
To be applied to Reinforcement Learning the problem needed to be specified as an Markov Decision Process. Utilize the MDP definition 
for a Load Distribution Problem in a Network with a central controller node following closely the definition of MDP the authors at "[Managing Fog Networks using Reinforcement
Learning Based Load Balancing Algorithm](https://ieeexplore.ieee.org/document/8885745)" give.

The MDP is defined as a tuple <S, A, P, R> where:
- **State Space, S**: S is the set of all possible states, this states are tuples $s = (n^l, w, Q)$. 
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
    - The Delay function has three components given by: $D(s, a) = X_d\frac{t^w + t^c + t^e}{w^l + w^o}$
      - $t^w$, average time a task will be sitting in the queue of the fog node $n^l$. The expression to compute $t^w$
      is $t^w = \frac{Q^l}{\mu^l}* \mathbb(1)(w^l \neq 0) + (\frac{Q^l}{\mu^l} + \frac{Q^o}{\mu^o})* \mathbb(1)(w^l \neq 0)$
      - d
      - d
    - b
### The Network Topology
In the network used in the paper, the nodes all can access each other directly and are distributed in a 100x100m area.

### REST API
To allow receiving instructions from the model in python we wrap the simulator in a simple REST API built on top of a Spring Server. The API has provides
two endpoints:

- GET: http://localhost:8080/state - This endpoint will return the state of the simulation at request time. 
- POST: http://localhost:8080/action -  This endpoint will send the offloading action specified in the body of the request to the simulator.
  and send the reward of taking the action in the response.


# Quickstart
This section will focus on two things. How to setup and utilize the environment, and how to build a new version of the simulation.
## Utilizing the Environment
Running the environment is simple. You need to setup your anaconda environment first, afterwards you just need to create a object 
`PeersimEnv("config_file.txt")` in pyhton this will start the server with the configurations you specify on a configuration file, more details on this later in this guide.

### Setup the anaconda environment
I provide a yml file to automatically install the necessary dependencies, see `Setup/PeersimGym.yml`. To create an environment from this specification run:

```
conda env create -f Setup/PeersimGym.yml
```

After creating the environment you need to activate it. This step may need to be repeated everytime you open the project
(No need to create the environment again, just activate it).

```
conda activate PeersimGym
```
### Using the environment
To start the simulation all you need to do is create a PeersimEnv object in your python code. This environment can then be used 
like a regular Gym environment, an example on that can be found [here](https://gymnasium.farama.org/)
``` 
Note: Still have to set up the gym.make part of it...
```

## Setup the simulator
I provide a jar with everything needed to run the project in `target/peersim-srv-0.0.1-SNAPSHOT.jar`. The following sections
are only to compile any changes made over the version provided in this repository. 
### Maven Dependencies
**Setup Maven**
First install maven itself, if you don't have already. To install it follow the instructions in https://maven.apache.org/install.html
After having maven installed. Add the private dependencies that come packaged with the Peersim project to maven.
To do that run the provided script that will add the dependencies to a local private repository:

**Linux**

```
./install-mvn-external-dependencies.sh
```

**Windows**

```
.\install-mvn-external-dependencies.ps1
```

### Compiling the Simulator
To compile the simulator use the following command:

```
mvn clean -Dmaven.test.skip package
```

This will package a jar with all necessary dependencies to run the simulator. The jar can be found on the `target/peersim-srv-0.0.1-SNAPSHOT.jar`
directory/folder.

## Configuring the Simulator

