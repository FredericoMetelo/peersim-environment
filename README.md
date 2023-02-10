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

## Setup the simulator
I should note that I provide a jar with everything needed to run the project in 

### Maven Dependencies
The project utilises some private jar's that come packaged with the Peersim project. This dependencies need to be added to rebuild the project with maven.

### Compiling the Simulator

## Configuring the Simulator

