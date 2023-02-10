# Peersim-Env

This repository contains the implementation of Peersim-env. This is composed of two parts,
a server that allows interaction with the simultion of the peersim environment, and a Gym environment class in python to
communicate with the gym environment.

### The Simulation Server

We build a simulation based on the Network used in the paper by J.-y. Baek, G. Kaddoum, S. Garg, K. Kaur and V. Gravel, "[Managing Fog Networks using Reinforcement
Learning Based Load Balancing Algorithm](https://ieeexplore.ieee.org/document/8885745)".

We utilize [Peersim](https://peersim.sourceforge.net/) to simulate a network with three components, that are:

- Worker Nodes: Simulate the execution of tasks, offload tasks on request by the controller and communicate status messages to the controller.
- Client Nodes: Only one client that will for each node generate and request tasks following a poisson process. the Client also keeps an
  average of the time the reuqests took to be processed in the time internal to the simulator.
- Controller Node: The controller nodes keep information of the status of each node and recieve the offload instructions from the outside to pass 
  them to the relevant worker node. The worker node to be offloaded to is selected on a round robin style across all nodes.

### The Network Topology
In the network used in the paper, the nodes all can access each other directly and are distributed in a 100x100m area.

### Spring Server
To allow receiving instructions from the model in python we wrap the simulator in a simple REST API built on top of a Spring Server. The API has provides
two endpoints:

- GET: http://localhost:8080/state - This endpoint will return the state olf the simulation at request time. 
- POST: http://localhost:8080/action -  This endpoint will send the offloading action specified in the body of the request to the simulator.
  and send the reward of taking the action in the response.


# Quickstart
## Executiong the Simulator
Running the environment is simple. You need to setup your anaconda environment first, afterwards you just need to create a object 
`PeersimEnv("config_file.txt")` in pyhton this will start the server with the configurations you specify on a configuration file, more details on this later in this guide.

### Setup the anaconda environment
I provide a .yml to automatically install the necessary dependencies, see `Setup/PeersimGym.yml`. To create an environment from this specification run:

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

