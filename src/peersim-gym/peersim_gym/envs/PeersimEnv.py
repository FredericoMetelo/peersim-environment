import math
import os
import time
from random import randint
from typing import Callable, Any

import gymnasium
import numpy as np
import requests
from gymnasium import Space
from gymnasium.spaces import MultiDiscrete, Dict, Discrete, Box, Sequence
from pettingzoo import ParallelEnv

import json
import peersim_gym.envs.Utils.PeersimConfigGenerator as cg
from peersim_gym.envs.Utils.FLUpdateStoreManager import FLUpdateStoreManager
from peersim_gym.envs.Utils.PeersimThread import PeersimThread
from peersim_gym.envs.Utils.PeersimVis import PeersimVis

import socket
from contextlib import closing

STATE_NO_NEIGHBOURS = "numberOfNeighbours"

STATE_G_LAYERS = "layers"

STATE_G_OVERLOADED_NODES = "overloadedNodes"
STATE_G_OVERLOADED_NODES_SIM = "timesOverloaded"

STATE_G_OCCUPANCY = "occupancy"

STATE_G_AVERAGE_COMPLETION_TIMES = "averageCompletionTimes"
STATE_G_IDS = "ids"
STATE_G_Q = "true_Q"
STATE_G_DROPPED_TASKS = "droppedTasks"
STATE_G_FINISHED_TASKS = "finishedTasks"
STATE_G_TOTAL_TASKS = "totalTasks"
STATE_G_CONSUMED_ENERGY = "energyConsumed"
STATE_G_DROPPED_BY_EXPIRED = "noExpired"
STATE_G_DROPPED_ON_ARRIVAL = "noFailedOnArrival"
STATE_G_TOTAL_RECEIVED_PER_NODE = "totalTasksReceived"
STATE_G_TOTAL_FINISHED_PER_NODE = "totalTasksProcessedPerNode"
STATE_G_OFFLOADED_TASKS_FROM_NODE = "totalTasksOffloadedFromNode"
STATE_G_OFFLOADED_TASKS_TO_NODE = "totalTasksOffloadedToNode"
STATE_G_AVERAGE_RT = "averageResponseTime"
STATE_G_TASK_RCV_SINCE_LAST_CYCLE = "tasksRecievedSinceLastCycle"
STATE_G_TASKS_DRP_SINCE_LAST_CYCLE = "droppedThisCycle"

STATE_NEXT_TASK = "nextTask"
STATE_TASKS_IN_QUEUE = "tasks"
STATE_TASK_PARAM_PROGRESS = "progress"
STATE_TASK_PARAM_TOTAL_INSTR = "totalInstructions"
STATE_TASK_PARAM_OUTPUT_SIZE = "outputSizeBytes"
STATE_TASK_PARAM_INPUT_SIZE = "inputSizeBytes"
STATE_TASK_PARAM_PROCESSED_LOCALLY = "processedLocally"

STATE_TASK_PARAM_ID = "id"

AGENT_PREFIX = "worker_"



def not_zero(num):
    return 1 if num > 0 else 0


def average_of_ints_in_string(s):
    '''
    Computes the average of a string of numbers separated by commas.
    '''
    # Convert the string to a list of integers
    numbers = [int(num) for num in s.strip().split(",")]
    # Compute and return the average
    return sum(numbers) / len(numbers) if numbers else 0


def average_of_floats_in_string(s):
    '''
    Computes the average of a string of numbers separated by commas.
    '''
    # Convert the string to a list of integers
    numbers = [float(num) for num in s.split(",")]
    # Compute and return the average
    return sum(numbers) / len(numbers) if numbers else 0


def validate_simulation_type(simtype):
    if simtype not in ['basic', 'batch', 'dag', 'basic-workload']:
        raise ValueError("Invalid argument: %s (NOTE: capitalization matters)" % simtype)
    if simtype == "dag":
        raise ValueError("dag is not yet supported")


ACTION_TYPE_FIELD = "type"
ACTION_NEIGHBOUR_IDX_FIELD = "neighbourIndex"
ACTION_HANDLER_ID_FIELD = "controllerId"
RESULT_DISTANCE_FIELD = "distance"
RESULT_WORKER_INFO_FIELD = "wi"
STATE_NODE_ID_FIELD = "nodeId"
STATE_Q_FIELD = "Q"
STATE_QSIZE_FIELD = "queueSize"
STATE_PROCESSING_POWER_FIELD = "processingPower"
STATE_FREE_SPACES_FIELD = "freeSpaces"
STATE_TASKQ_AGGR_TOTAL_INSTR = "totalInstructions"
STATE_TASKQ_AGGR_TOTAL_LOCAL = "totalLocallyProcessed"




# def can_launch_simulation():
#     with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
#         return sock.connect_ex(('localhost', 8080)) != 0

def can_launch_simulation(port):
    # for port in range(8080, 8090):
    #         with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
    #             if sock.connect_ex(('localhost', port)) == 0:
    #                 print(f"Port {port} is available")
    #                 return port
    max_attempts = 10
    for i in range(max_attempts):
        print(f"Trying to launch sim on port {port}. Attempt {i}")
        if test_port_availability(port):
            print(f"Port {port} is available")
            return port
    return None

def test_port_availability(port):
    with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
        # If you can connect to the port it means that the port is already listening.
        return sock.connect_ex(('localhost', port)) != 0

class PeersimEnv(ParallelEnv):
    metadata = {"render_modes": ["ansi", "human"], "render_fps": 4}

    def init(self, render_mode=None, configs=None, log_dir=None, randomize_seed=False,
             phy_rs_term: Callable[[Space], float] = None):
        self.__init__(render_mode, configs, log_dir=log_dir, randomize_seed=randomize_seed, phy_rs_term=phy_rs_term)

    def __init__(self, render_mode=None, simtype="basic", configs=None, log_dir=None, randomize_seed=False, preferred_port=8080,
                 phy_rs_term: Callable[[Space], float] = None, fl_update_size: Callable[[Any], int] = None, state_info="none", reward_type="sparse"):
        # ==== Variables to configure the PeerSim
        # This value does not include the controller, SIZE represents the total number of nodes which includes
        # the controller.
        # (aka if number_nodes is 10 there is a total of 11 nodes (1 controller + 10 workers))

        if reward_type not in ["sparse", "dense"]:
            raise ValueError("Invalid reward type. Use 'sparse' or 'dense'.")

        self.reward_type = reward_type
        self.phy_rs_term = phy_rs_term
        validate_simulation_type(simtype)
        self.render_mode = render_mode
        self.randomize_seed = randomize_seed
        self.port = preferred_port
        if not test_port_availability(self.port):
            print(f"Port {self.port} is not available. Trying to find a new port.")
            self.port = can_launch_simulation(self.port)

        if self.port is None:
            print("Simulation Failed to launch. No ports available, please free a port in range  8080-8089 first.")
            exit(1)

        self.number_nodes = -1
        self.max_Q_size = [10, 50]  # TODO this is specified from outside.
        self.max_w = 1

        self.fl_update_store = FLUpdateStoreManager(fl_update_size)
        self.url_api = f"http://localhost:{self.port}"
        print("API URL: " + self.url_api)
        self.url_action_path = "/action"
        self.url_forward_path = "/forward"
        self.url_state_path = "/state"
        self.url_isUp = "/up"
        self.url_isStopped = "/stopped"
        self.url_NetworkData = "/NeighbourData"
        self.url_FL_get_updates = "/fl/done"
        self.url_FL_send_update = "/fl/update"

        self.default_timeout = 3  # Second

        self.config_archive = configs
        self.simtype = simtype

        self.controllers = self.__gen_config(configs, simtype=simtype, config_port=self.port)
        print(f"Config paht: {self.config_path}")

        self.number_nodes = int(self.config_archive["SIZE"] ) + int(self.config_archive["CLOUD_EXISTS"] if "CLOUD_EXISTS" in self.config_archive else 0)
        self.possible_agents = [AGENT_PREFIX + str(r) for r in self.controllers]
        self.agent_name_mapping = dict(
            zip(self.possible_agents, self.controllers)
        )
        # ---- State Space
        self.__log_dir = log_dir
        self.__run_counter = 0

        self.no_nodes_per_layer = [int(no_nodes) for no_nodes in
                                   self.config_archive["NO_NODES_PER_LAYERS"].strip().split(",")]

        self.layers_that_get_tasks = [int(layer) for layer in
                                      self.config_archive["layersThatGetTasks"].strip().split(",")]
        if isinstance(self.max_Q_size, list):
            self.q_list = self._gen_node_Q_max()

        else:
            self.q_list = [self.max_Q_size for _ in range(self.number_nodes)]

        # Options are: none, next, queue, queue_next
        # Eventually, convert this to a wrapper.

        task_space = Dict({
            STATE_TASK_PARAM_PROCESSED_LOCALLY: Discrete(2),
            STATE_TASK_PARAM_ID: Discrete(self.number_nodes),
            STATE_TASK_PARAM_PROGRESS: Box(low=0, high=np.inf, dtype=float),
            STATE_TASK_PARAM_TOTAL_INSTR: Box(low=0, high=np.inf, dtype=float),
            STATE_TASK_PARAM_INPUT_SIZE: Box(low=0, high=np.inf, dtype=float),
            STATE_TASK_PARAM_OUTPUT_SIZE: Box(low=0, high=np.inf, dtype=float)
        })

        self._observation_spaces = {}
        for agent in self.possible_agents:
            base_space = {
                STATE_NODE_ID_FIELD: Discrete(self.number_nodes, start=1),  # Ignores the controller
                STATE_Q_FIELD: MultiDiscrete(self.q_list[self.agent_name_mapping[agent]]),
                STATE_FREE_SPACES_FIELD: MultiDiscrete(self.q_list[self.agent_name_mapping[agent]]),
                STATE_PROCESSING_POWER_FIELD: Box(high=self.max_w, low=0, dtype=float)
            }

            if state_info == "queue_next":
                base_space[STATE_NEXT_TASK] = task_space
                base_space[STATE_TASKS_IN_QUEUE] = Sequence(task_space)
            elif state_info == "next":
                base_space[STATE_NEXT_TASK] = task_space
            elif state_info == "queue":
                base_space[STATE_TASKS_IN_QUEUE] = Sequence(task_space)
            elif state_info == "qaggr":
                base_space[STATE_TASKQ_AGGR_TOTAL_INSTR] = Box(low=0, high=np.inf, dtype=float)
                base_space[STATE_TASKQ_AGGR_TOTAL_LOCAL] = Box(low=0, high=np.inf, dtype=float)
            elif state_info == "qaggr_next":
                base_space[STATE_NEXT_TASK] = task_space
                base_space[STATE_TASKQ_AGGR_TOTAL_INSTR] = Box(low=0, high=np.inf, dtype=float)
                base_space[STATE_TASKQ_AGGR_TOTAL_LOCAL] = Box(low=0, high=np.inf, dtype=float)
            # 'none' means only base_space is used

            self._observation_spaces[agent] = Dict(base_space)


    # ---- Action Space
        self._action_spaces = {
            agent: Dict(
                {
                    # ACTION_HANDLER_ID_FIELD: Discrete(self.number_nodes - 1, start=0),
                    ACTION_NEIGHBOUR_IDX_FIELD: Discrete(self.number_nodes - 1, start=0)
                    # The neighbour action is not actually part of the actions taken by the agents.
                }
            ) for agent in self.possible_agents
        }

        # mvn spring-boot:run -Dspring-boot.run.arguments=configs/default-config.txt
        # Run the actual environment.
        # Prepare the Configuration file

        # with subprocess as s:
        self.simulator = None
        self.state_info = state_info
        # self.simulator = PeersimThread(name='Run0', configs=self.config_path)
        # self.simulator.run()
        self.UTILITY_REWARD = self.config_archive["utility_reward"]
        self.DELAY_WEIGHT = self.config_archive["delay_weight"]
        self.OVERLOAD_WEIGHT = self.config_archive["overload_weight"]
        self.NO_OP_REWARD = self.config_archive["no_op_reward"] if "no_op_reward" in self.config_archive else 0

        self.scale = int(self.config_archive["SCALE"])
        self.TRANSMISSION_POWER = float(self.config_archive["protocol.props.P_ti"])
        self.PATH_LOSS_CONSTANT = float(self.config_archive["protocol.props.Beta1"])
        self.PATH_LOSS_EXPONENT = float(self.config_archive["protocol.props.Beta2"])
        self.BANDWIDTH = float(self.config_archive["protocol.props.B"])
        self.NORMALIZED_THERMAL_NOISE_POWER = -174
        self.AVERAGE_TASK_SIZE, self.AVERAGE_TASK_INSTR, self.TASK_ARRIVAL_RATE = self._compute_avg_task_data()
        self.AVERAGE_PROCESSING_POWER, self.AVERAGE_MAX_Q, self.PROCESSING_POWERS = self._compute_worker_data()
        self.NODES_PER_LAYER = [int(no_nodes) for no_nodes in
                                self.config_archive["NO_NODES_PER_LAYERS"].strip().split(",")]
        self.avg_neighbours = -1
        self.min_neighbours = -1
        self.max_neighbours = -1
        self.neighbourMatrix = []
        self.clients_per_node = []
        self.clients_per_layer = [int(no_nodes) for no_nodes in self.config_archive["clientLayers"].strip().split(",")]
        self.has_cloud = int(self.config_archive["CLOUD_EXISTS"])

        self.last_reward_components = {}

        if self.render_mode == "human":
            self.vis = PeersimVis(self.has_cloud, int(self.config_archive["CYCLES"]), self.config_archive["SCALE"])

    def observation_space(self, agent):

        base_space = {
            STATE_NODE_ID_FIELD: Discrete(self.number_nodes, start=1),  # Ignores the controller
            STATE_Q_FIELD: MultiDiscrete(self.q_list),
            STATE_FREE_SPACES_FIELD: MultiDiscrete(self.q_list),
            STATE_PROCESSING_POWER_FIELD: Box(high=self.max_w, low=0, dtype=float)
        }

        task_space = Dict({
            STATE_TASK_PARAM_PROCESSED_LOCALLY: Discrete(2),
            STATE_TASK_PARAM_ID: Discrete(self.number_nodes),
            STATE_TASK_PARAM_PROGRESS: Box(low=0, high=np.inf, dtype=float),
            STATE_TASK_PARAM_TOTAL_INSTR: Box(low=0, high=np.inf,dtype=float),
            STATE_TASK_PARAM_INPUT_SIZE: Box(low=0, high=np.inf,dtype=float),
            STATE_TASK_PARAM_OUTPUT_SIZE: Box(low=0, high=np.inf,dtype=float)
        })

        if self.state_info == "queue_next":
            base_space[STATE_NEXT_TASK] = task_space
            base_space[STATE_TASKS_IN_QUEUE] = Sequence(task_space)
        elif self.state_info == "next":
            base_space[STATE_NEXT_TASK] = task_space
        elif self.state_info == "queue":
            base_space[STATE_TASKS_IN_QUEUE] = Sequence(task_space)
        elif self.state_info == "qaggr":
            base_space[STATE_TASKQ_AGGR_TOTAL_INSTR] = Box(low=0, high=np.inf, dtype=float)
            base_space[STATE_TASKQ_AGGR_TOTAL_LOCAL] = Box(low=0, high=np.inf, dtype=float)
        elif self.state_info == "qaggr_next":
            # Iterate over tasks for each agent. Prepare the aggregate metrics
            base_space[STATE_NEXT_TASK] = task_space
            base_space[STATE_TASKQ_AGGR_TOTAL_INSTR] = Box(low=0, high=np.inf, dtype=float)
            base_space[STATE_TASKQ_AGGR_TOTAL_LOCAL] = Box(low=0, high=np.inf, dtype=float)
        return Dict(base_space)


    def action_space(self, agent):
        return Dict(
            {
                # ACTION_HANDLER_ID_FIELD: Discrete(self.number_nodes - 1, start=0),
                ACTION_NEIGHBOUR_IDX_FIELD: Discrete(self.number_nodes - 1, start=0)
            }
        )

    def reset(self, **kwargs):

        self.agents = self.possible_agents[:]
        self.num_moves = 0
        if self.randomize_seed:
            self.set_random_seed()
        if self.simulator != None:
            self.simulator.stop()
        else:
            self.simulator = PeersimThread(name=f'Run{self.__run_counter}', configs=self.config_path)
        # self.__gen_config(self.config_archive, regen_seed=True)
        self.__run_peersim()

        time.sleep(10)
        up = self.__is_up()
        stopped = self.__is_stopped()
        tries = 0
        while not up or not stopped:
            time.sleep(0.5)
            print("Attempting to connect to the environment")
            up = self.__is_up()
            stopped = self.__is_stopped()
            tries += 1
        print("Server is up")

        self.has_cloud = int(self.config_archive["CLOUD_EXISTS"])
        observations, done, info = self.__get_obs()
        self.poolNetStats()
        self.fl_update_store.passNeighbourMatrix(self.neighbourMatrix)
        observations = self.normalize_observations(observations)


        infos = {agent: self.build_agent_info(info, agent) for agent in self.agents}

        return observations, infos

    def step(self, actions):
        if not self.__is_up() or actions is None:
            self.agents = []
            return {}, {}, {}, {}, {}
        original_obs = self.state
        mask = self._validateAction(original_obs, actions)
        if len(actions) == 0:
            result = self.__forward_environment()
        else:
            result = self.__send_action(actions)

        # Wait for the simulation to stabilize so the next state is actually the next state the agent would observe.
        while not self.__is_stopped():
            time.sleep(0.05)

        observations, done, info = self.__get_obs()
        agent_info = {agent: self.build_agent_info(info, agent) for agent in self.agents}

        rewards = self._compute_rewards(original_obs, observations, actions, result, mask, agent_info)
        # print(f"\033[31m DEBUG:   {rewards} \033[0m")

        observations = self.normalize_observations(observations)

        terminations = {agent: done for agent in self.agents}
        self.num_moves += 1
        truncations = {agent: False for agent in self.agents}

        self.last_actions = actions

        if self.render_mode is not None:
            self.render()

        return observations, rewards, terminations, truncations, info

    def render(self):
        if self.render_mode == "ansi":
            return self.__render_ansi()
        elif self.render_mode == "human":
            return self.__render_human()

    def __render_ansi(self):
        print(json.dumps(self.state))
        print(json.dumps(self._info))
        print("Last Reward Components:" + json.dumps(self.last_reward_components))

    def __render_human(self):
        self.vis.update_state(self._global_obs, self.max_Q_size, self.neighbourMatrix, self.last_actions,
                              self.controllers, self.agent_name_mapping, self._result)
        self.vis.draw()

    def close(self):
        self.simulator.stop()


    def post_updates(self, agents, srcs, dst, updates, extra_info=None, sent_to_global=False):
        """
        (Outside of PettingZoo API)
        This method works by passing a list of FL updates through the network. It adds the updates to the list of
        updates awaiting return, and then requests the sending of the updates through the simulation.

        :param agents list of agents sending the updates:
        :param srcs dictionary with the id of the node sendign the update:
        :param dst dictioary with the index in the neighbour list of the node being targeted by the update:
        :param updates dictionary with the updates to be sent:
        :return:
        """
        update_list = []
        for idx, agent in enumerate(agents):
            update_entry = self.fl_update_store.store_update(agent, srcs[idx], dst[idx], updates[idx], sent_to_global,  {key: extra_info[key][idx] for key in extra_info} if extra_info is not None else None)
            formatted_entry = {
                "uuid": update_entry["uuid"],
                "src": update_entry["src_id"],
                "dst": update_entry["dst_idx"],
                "size": update_entry["size"]

            }
            update_list.append(formatted_entry)
        # Send the list in the body to the server
        payload = json.dumps(update_list)
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_FL_send_update
        try:
            r = requests.post(action_url, payload, headers=headers_action, timeout=self.default_timeout)
            if r.status_code == 200:
                return True
            else:
                return False
        except requests.exceptions.Timeout:
            print("Failed  to send action, could not connect to the environment. Returning old result.")

    def get_updates(self, agent):
        """
        (Outside of PettingZoo API)
        This method returns the updates that have arrived at their destination (the given agent's counterpart in the simulation).
        If the agent has no currently available updates then an empty list is returned.
        :param agent:
        :return:
        """
        self.fetch_available_updates_from_sim()
        updates = self.fl_update_store.get_update_per_agent(agent)
        return updates

    def fetch_available_updates_from_sim(self):
        """
        This method is responsible for fetching the updates from the simulation. And passing them to the FL Update Store
        """
        payload = {}
        headers_action = {"Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_FL_get_updates
        r = requests.get(action_url, json=payload, headers=headers_action, timeout=self.default_timeout).json()
        for update_uuid in r:
            self.fl_update_store.set_completed(update_uuid)

    def __gen_config(self, configs, simtype, regen_seed=False, config_port=8080):
        controller = []
        # Checking the configurations
        if configs is None:
            configs = {"Q_MAX": str(self.max_Q_size), "SIZE": str(self.number_nodes),
                       "random.seed": self.__gen_seed(), "HAS_CLOUD": str(self.has_cloud)}

            controller, self.config_path = cg.generate_config_file(configs, simtype, sim_port=config_port)
        elif type(configs) is dict:
            # Consistency of QMax in configs and the arguments.
            if "Q_MAX" in configs:
                self.max_Q_size = [int(q) for q in configs["Q_MAX"].strip().split(",")]

            if "SIZE" in configs:
                self.number_nodes = int(configs["SIZE"])
                if "CLOUD_EXISTS" in configs:
                    self.has_cloud = int(configs["CLOUD_EXISTS"])
                    self.number_nodes += self.has_cloud
            # - 1 Not anymore

            if not ("random.seed" in configs) or regen_seed:
                configs["random.seed"] = self.__gen_seed()
                print(f'seed:{configs["random.seed"]}')

            controller, self.config_path = cg.generate_config_file(configs, simtype, sim_port=config_port)
        elif type(configs) is str:
            self.config_path = configs
            controller, self.config_archive = cg.compile_dict(configs)
        else:
            raise Exception(
                "Invalid Type for the configs parameter. Needs to be None, a Dictionary or a String. Please see the project README.md")

        return controller

    def __run_peersim(self):
        self.__run_counter += 1
        log_file = None
        if not (self.__log_dir is None):
            log_file = self.__log_dir + f'log_run_{self.__run_counter}.txt'
            if os.path.exists(log_file):
                try:
                    os.remove(log_file)
                except OSError as e:
                    print(f"Error: {e.strerror}")
        self.simulator.run(port=self.port, output_file=log_file)

    def __get_obs(self):
        # Retrieve Observation
        space_url = self.url_api + self.url_state_path
        headers_state = {"Accept": "application/json", "Connection": "keep-alive"}
        try:
            iter = 0
            r = requests.get(space_url, headers=headers_state, timeout=self.default_timeout)
            while r.status_code < 200 or r.status_code >= 300:  # Most likely only 200 will be returned, but I'm paranoid
                r = requests.get(space_url, headers=headers_state, timeout=self.default_timeout)
                iter += 1
                if iter > 100:
                    print("Failed to get observation, connection timed out. Returning old reward.")
                    raise Exception("Failed to get observation after 100 tries, environment is down.")
                time.sleep(0.1)
            s = r.json()
            full_obs = s.get('state')
            global_obs = full_obs.get('globalState')
            partial_obs = full_obs.get('observedState')
            done = s.get("done")
            self._observation = partial_obs
            self._global_obs = global_obs
            self._done = done

            self._dbg_info = s.get("info", {})
            # TODO: Add a modification of the partial observations here, to filter out the information the users does not
            #  want
            observations = {self.agents[i]: self.extract_local_data(partial_obs[i]) for i in range(len(self.agents))}
            self.state = observations
            extracted_data = self.extract_global_data(global_obs, self._dbg_info)
            dbg_info = {
                STATE_G_Q: global_obs[STATE_Q_FIELD],
                STATE_G_OVERLOADED_NODES: extracted_data[0],
                STATE_G_OCCUPANCY: extracted_data[1],
                STATE_G_AVERAGE_COMPLETION_TIMES: extracted_data[2],
                STATE_G_DROPPED_TASKS: extracted_data[3],
                STATE_G_FINISHED_TASKS: extracted_data[4],
                STATE_G_TOTAL_TASKS: extracted_data[5],
                STATE_G_CONSUMED_ENERGY: extracted_data[6],
                STATE_G_OVERLOADED_NODES_SIM: extracted_data[7],
                STATE_G_DROPPED_BY_EXPIRED: extracted_data[8],
                STATE_G_DROPPED_ON_ARRIVAL: extracted_data[9],
                STATE_G_TOTAL_RECEIVED_PER_NODE: extracted_data[10],
                STATE_G_OFFLOADED_TASKS_FROM_NODE: extracted_data[11],
                STATE_G_TOTAL_FINISHED_PER_NODE: extracted_data[12],
                STATE_G_OFFLOADED_TASKS_TO_NODE: extracted_data[13],
                STATE_G_IDS:extracted_data[14],
                STATE_G_TASK_RCV_SINCE_LAST_CYCLE: extracted_data[15],
                STATE_G_TASKS_DRP_SINCE_LAST_CYCLE: extracted_data[16],
                STATE_G_AVERAGE_RT: extracted_data[17]

                # TODO Add the new information here!!! Confirm the order is correct, bcs added ids!!! Could hv borked (did fr
            }

            self._info = dbg_info
            return observations, done, dbg_info
        except requests.exceptions.Timeout:
            print("Failed to get observation, connection timed out. Returning old reward.")
            return self._observation, True, self._info

    def __send_action(self, action):

        payload = [
            {
                ACTION_TYPE_FIELD: self.simtype.split("-")[0],
                ACTION_NEIGHBOUR_IDX_FIELD: int(action.get(name).get(ACTION_NEIGHBOUR_IDX_FIELD)),
                ACTION_HANDLER_ID_FIELD: int(self.agent_name_mapping.get(name))
            }  for name in self.agent_name_mapping.keys() if name in action.keys()
        ]
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_action_path
        try:
            payload_json = json.dumps(payload)
            # print(payload_json)
            r = requests.post(action_url, payload_json, headers=headers_action, timeout=self.default_timeout)
            r = r.json()
            result = {AGENT_PREFIX + str(reward["srcId"]): reward for reward in r}
            self._result = result
            return result
        except requests.exceptions.Timeout:
            print("Failed  to send action, could not connect to the environment. Returning old result.")
            return self._result

    def __get_net_data(self):
        payload = {}
        headers_action = {"Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_NetworkData
        r = requests.get(action_url, json=payload, headers=headers_action, timeout=self.default_timeout).json()
        min = r.get('min')
        max = r.get('max')
        avg = r.get('average')
        neighbourMatrix = r.get('neighbourMatrix')
        knownControllersMatrix = r.get('knowsControllerMatrix')
        return min, max, avg, neighbourMatrix, knownControllersMatrix

    def __is_up(self):
        payload = {}
        headers_action = {"Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_isUp
        try:
            r = requests.get(action_url, json=payload, headers=headers_action, timeout=self.default_timeout)
            status = r.content == 'True' or r.content == 'true' or r.content
        except requests.exceptions.Timeout:
            print("Failed at Connecting to the Environment")
            status = False
        except requests.exceptions.ConnectionError:
            # if the Server is not yet reachable. We wait.
            status = False

        return status

    def __is_stopped(self):
        payload = {}
        headers_action = {"Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_isStopped
        try:
            r = requests.get(action_url, json=payload, headers=headers_action, timeout=self.default_timeout)
            status = r.content == 'True' or r.content == 'true' or r.content
        except requests.exceptions.Timeout:
            print("Failed at Connecting to the Environment")
            status = False
        except requests.exceptions.ConnectionError:
            # if the Server is not yet reachable. We wait.
            status = False

        return status



    def build_agent_info(self, info, agent):
        agent_id = self.agent_name_mapping[agent]
        i = 0

        for n in info[STATE_G_IDS]:
            if n == agent_id:
                agent_id = i
                break
            i+=1

        agent_info = {
            STATE_G_Q: info[STATE_G_Q][agent_id],
            STATE_G_OVERLOADED_NODES: info[STATE_G_OVERLOADED_NODES][agent_id],
            STATE_G_OCCUPANCY: info[STATE_G_OCCUPANCY][agent_id],
            # These are all client metrics, so not aligned with workers... And make no sense for inclusion in the agent info.
            # STATE_G_AVERAGE_COMPLETION_TIMES: info[STATE_G_AVERAGE_COMPLETION_TIMES][agent_id],
            # STATE_G_DROPPED_TASKS: info[STATE_G_DROPPED_TASKS][agent_id],
            # STATE_G_FINISHED_TASKS: info[STATE_G_FINISHED_TASKS][agent_id],
            # STATE_G_TOTAL_TASKS: info[STATE_G_TOTAL_TASKS][agent_id],
            STATE_G_CONSUMED_ENERGY: info[STATE_G_CONSUMED_ENERGY][agent_id],
            STATE_G_OVERLOADED_NODES_SIM: info[STATE_G_OVERLOADED_NODES_SIM][agent_id],
            STATE_G_DROPPED_BY_EXPIRED: info[STATE_G_DROPPED_BY_EXPIRED][agent_id],
            STATE_G_DROPPED_ON_ARRIVAL: info[STATE_G_DROPPED_ON_ARRIVAL][agent_id],
            STATE_G_TOTAL_RECEIVED_PER_NODE: info[STATE_G_TOTAL_RECEIVED_PER_NODE][agent_id],
            STATE_G_OFFLOADED_TASKS_FROM_NODE: info[STATE_G_OFFLOADED_TASKS_FROM_NODE][agent_id],
            STATE_G_TOTAL_FINISHED_PER_NODE: info[STATE_G_TOTAL_FINISHED_PER_NODE][agent_id],
            STATE_G_OFFLOADED_TASKS_TO_NODE: info[STATE_G_OFFLOADED_TASKS_TO_NODE][agent_id],
            STATE_G_IDS: info[STATE_G_IDS][agent_id],
            STATE_G_TASK_RCV_SINCE_LAST_CYCLE: info[STATE_G_TASK_RCV_SINCE_LAST_CYCLE][agent_id],
            STATE_G_TASKS_DRP_SINCE_LAST_CYCLE: info[STATE_G_TASKS_DRP_SINCE_LAST_CYCLE][agent_id],
            STATE_G_AVERAGE_RT: info[STATE_G_AVERAGE_RT][agent_id],
        }
        return agent_info

    def __gen_seed(self):
        n = randint(1000000000, 9999999999)
        return str(n)

    def _compute_rewards(self, original_obs, obs, actions, result, mask, info) -> dict[float]:
        rewards = {}
        for agent in self.agents:  # Refer to r/Arkham for the correct thing to ask about whomever (Man) left this here...
            if agent in actions and not mask[agent]:
                if self.reward_type is "dense":
                    p = self._compute_dense_reward(
                        agent_og_obs=original_obs[agent],
                        agent_obs=obs[agent],
                        action=actions[agent],
                        agent_result=result[agent],
                        agent_idx=self.agent_name_mapping[agent],
                        agent_info= info[agent]
                    )
                else: # Where is sane, and why is everyone going in?
                    p = self._compute_sparse_reward(
                        agent_og_obs=original_obs[agent],
                        agent_obs=obs[agent],
                        action=actions[agent],
                        agent_result=result[agent],
                        agent_idx=self.agent_name_mapping[agent],
                        agent_info= info[agent]
                    )
                rewards[agent], self.last_reward_components[agent] = p

                #print(f"Agent {agent} got reward {rewards[agent]}")
            elif agent in actions and mask[agent]:
                rewards[agent] = -self.UTILITY_REWARD
                #print(f"Agent {agent} got reward {rewards[agent]} (F)")

            # else:
            #     print(f"Action of agent {agent} was not found in the actions sent.")
        return rewards

    def _compute_dense_reward(self, agent_og_obs, agent_obs, action, agent_result, agent_idx, agent_info):
        # Long story short... having a large range of rewards is making it so that the agent is not learning. Q-values
        # are exploding. The kind people in stack exchange have recommended that I keep the rewards in a small range [-1, 1]
        # https://datascience.stackexchange.com/questions/20098/should-i-normalize-rewards-in-reinforcement-learning

        # Prepare data
        source_of_task_global_index = agent_obs[STATE_NODE_ID_FIELD]
        target_of_task_neighbourhood_index = action[ACTION_NEIGHBOUR_IDX_FIELD]

        source_node_og_info = agent_og_obs
        source_node_info = agent_obs
        target_node_worker_info = agent_result[RESULT_WORKER_INFO_FIELD]
        target_of_task_global_index = target_node_worker_info["id"]
        locally_processed = agent_obs[STATE_NODE_ID_FIELD] == target_node_worker_info["id"]

        # Check if the target node is within [0, #Neighbours]
        if int(len(source_node_og_info["Q"])) < int(target_of_task_neighbourhood_index) or int(
                target_of_task_neighbourhood_index) < 0:
            return -self.UTILITY_REWARD, {"U": -self.UTILITY_REWARD, "D": 0, "O": 0} # I think the little shits are exploiting this...

        target_layer = self.get_layer(target_of_task_global_index)
        target_processing_power = self.PROCESSING_POWERS[target_layer]
        target_max_q = self.max_Q_size[target_layer]

        if self.neighbourMatrix is None or len(self.neighbourMatrix) == 0:
            print("Neighbour Matrix is empty or none. Setting ranks to 1")
            source_rank = 1
            target_rank = 1
        else:
            source_rank = self.clients_per_node[source_of_task_global_index] if self.get_layer(
                source_of_task_global_index) in self.layers_that_get_tasks else 0
            target_rank = self.clients_per_node[target_of_task_global_index] if self.get_layer(
                target_of_task_global_index) in self.layers_that_get_tasks else 0

        source_layer = self.get_layer(source_of_task_global_index)
        source_processing_power = self.PROCESSING_POWERS[source_layer]
        source_max_q = self.max_Q_size[source_layer]

        q_l = source_node_og_info["queueSize"]
        q_o = target_node_worker_info["queueSize"]

        lambda_tar = self.TASK_ARRIVAL_RATE/self.scale
        source_var = lambda_tar * source_rank - source_processing_power / self.AVERAGE_TASK_INSTR
        target_var = lambda_tar * target_rank - target_processing_power / self.AVERAGE_TASK_INSTR
        q_expected_l = q_l if locally_processed else max(q_l - 1, 0)
        q_expected_o = q_o if locally_processed else q_o + 1

        q_expected_l = max(min(q_expected_l + source_var, source_max_q), 0)
        q_expected_o = max(min(q_expected_o + target_var, target_max_q), 0)

        d_i_j = agent_result[RESULT_DISTANCE_FIELD]

        w_o = 1 if not locally_processed and 1 <= q_l else 0
        w_l = 1 if locally_processed and 1 <= q_l else 0

        if w_l == 0 and w_o == 0:  # queue is empty, nothing to do, no penalty or reward given.
            print(f"NO OP - ID={agent_idx}: Empty queue. No action taken.")
            return self.NO_OP_REWARD, {"U": self.NO_OP_REWARD, "D": 0, "O": 0}

        miu_l = source_processing_power
        miu_o = target_processing_power

        # Compute Utility:
        U = self.UTILITY_REWARD  # * math.log(1 + w_l + w_o)

        # Compute Delay
        q_l = (q_l - 1)  if (q_l - 1) >= 0 else 0 # Patches the considering of the task processing time twice. In t_w and in t_e
        t_w = not_zero(w_l) * (q_l * self.AVERAGE_TASK_INSTR / miu_l) + not_zero(w_o) * ((q_l * self.AVERAGE_TASK_INSTR/ miu_l) + (
                    q_o / miu_o))  # q_l/miu_l on the second term represents the time spent waiting in queue before being selected for offloading
        t_c = self._compute_delay(d_i_j, w_o)

        # og: t_e = self.AVERAGE_TASK_INSTR * (w_l / source_processing_power + w_o / target_processing_power)
        t_e = self.AVERAGE_TASK_INSTR / target_processing_power - self.AVERAGE_TASK_INSTR / source_processing_power

        t_w = min(t_w, self.UTILITY_REWARD * self.DELAY_WEIGHT["queue"])
        t_e = max(min(t_e, self.UTILITY_REWARD * self.DELAY_WEIGHT["exec"]), -self.UTILITY_REWARD * self.DELAY_WEIGHT["exec"])
        t_c = min(t_c, self.UTILITY_REWARD * self.DELAY_WEIGHT["comm"])
        # t_w = t_w * self.DELAY_WEIGHT["queue"]
        # t_e = t_e * self.DELAY_WEIGHT["exec"]
        # t_c = t_c * self.DELAY_WEIGHT["comm"]

        D = t_w + t_c + t_e  # / (w_l + w_o)

        # Compute Overload
        # q_prime_l = q_expected_l  # min(max(0, q_l - avg_tasks_processed_per_node) + w_l, self.AVERAGE_MAX_Q)
        # q_prime_o = q_expected_o  # min(max(0, q_o - avg_tasks_processed_per_node) + w_o, self.AVERAGE_MAX_Q)
        # p_overload_l = max(0.0, self.TASK_ARRIVAL_RATE - q_prime_l)
        # p_overload_o = max(0.0, self.TASK_ARRIVAL_RATE - q_prime_o)

        distance_to_Ovl_l = max((source_max_q - q_expected_l) / source_max_q, 0.001)  # Normalized manhattan distance
        distance_to_Ovl_o = max((target_max_q - q_expected_o) / target_max_q, 0.001)  # 0.0001 is to avoid log(0)
        O = -math.log10(w_l * distance_to_Ovl_l + w_o * distance_to_Ovl_o)  # we subtract O, therfore the minus
        # Was using the ln before, now using log. Plus I project the distance to be between

        # Capping the percentages to be between 100 and -100
        # U = max(min(U, self.UTILITY_REWARD), self.UTILITY_REWARD) / self.UTILITY_REWARD
        # Some people call this cheating, I call it not despairing -, _ ,-.

        O = O * self.OVERLOAD_WEIGHT  # I cap the delay distance at 0.001 (0.1% to overload) therefore the log will only go down to 3

        # computing reward and normalizing it
        reward = U - (D + O)
        F = 0
        if self.phy_rs_term is not None:
            F = self.phy_rs_term(agent_obs) - self.phy_rs_term(agent_og_obs)
        reward += F
        print(f"Action:{source_of_task_global_index}->{target_of_task_global_index}      Reward:{reward}  Composed of - U:{U} | D:{D} [t_C {t_c} ; t_w {t_w} ; t_e {t_e}] | O:{O}) | F:{F}")
        return (reward, {"U": U, "D": D, "O": O, "F": F})

    def _compute_delay(self, d_i_j, w_o):
        if d_i_j == 0:
            t_c = 0
        else:
            T = self.AVERAGE_TASK_SIZE * 8e6;
            W = self.BANDWIDTH * 1e6;
            lambda_wavelength = 3e8 / 2.4e9
            P_t = self.TRANSMISSION_POWER
            N_0 = self.NORMALIZED_THERMAL_NOISE_POWER + 10 * math.log10(W)
            h = 10 * math.log10((lambda_wavelength ** 2) / (16 * math.pi ** 2)) - 20 * math.log10(d_i_j)

            SNR_db = P_t + h - N_0
            SNR_linear = 10 ** (SNR_db / 10)
            C = W * math.log2(1 + SNR_linear)

            t_c = w_o * T / C
        return t_c

    def _compute_avg_task_data(self):
        """
        This will be updated to include to mecanisms. If MANUAL_CONFIG is true. Use the MANUAL_CORES and MANUAL_FREQS instead.
        :return:
        """
        task_sizes = self.config_archive["protocol.clt.T"].strip().split(",")
        acc_task_size = 0
        task_instr = self.config_archive["protocol.clt.I"].strip().split(",")
        task_instr_size = self.config_archive["protocol.clt.CPI"].strip().split(",")
        acc_task_instr = 0
        task_weights = self.config_archive["protocol.clt.weight"].strip().split(",")
        acc_weights = 0


        task_arrival_rate = float(self.config_archive["protocol.clt.taskArrivalRate"])

        for i in range(len(task_weights)):
            acc_weights = float(task_weights[i])
            acc_task_instr = (float(task_instr[i]) * float(task_instr_size[i])) * int(task_weights[i])
            acc_task_size = float(task_sizes[i]) * float(task_weights[i])

        return acc_task_size / acc_weights, acc_task_instr / acc_weights, task_arrival_rate

    def _compute_worker_data(self):
        frequencies = [int(f) for f in self.config_archive["FREQS"].strip().split(",")]
        no_cores = [int(c) for c in self.config_archive["NO_CORES"].strip().split(",")]
        processing_power = [f * c for f, c in zip(frequencies, no_cores)]

        average_frequency = average_of_floats_in_string(self.config_archive["FREQS"])
        average_no_cores = average_of_ints_in_string(self.config_archive["NO_CORES"])
        average_max_Q = average_of_ints_in_string(self.config_archive["Q_MAX"])
        return float(average_no_cores) * float(average_frequency), int(average_max_Q), processing_power


    def _compute_sparse_reward(self, agent_og_obs, agent_obs, action, agent_result, agent_idx, agent_info):
        # TODO add the necessary information to the result being read.
        no_fin = len(agent_result['tasksCompleted'])
        no_fail = agent_info[STATE_G_TASKS_DRP_SINCE_LAST_CYCLE]

        # Then check for number of completed tasks for each node, add that as reward. Check for number of tasks dropped, add that as a penalty.

        r =no_fin*self.UTILITY_REWARD - no_fail*self.UTILITY_REWARD
        F = 0
        if self.phy_rs_term is not None:
            F = self.phy_rs_term(agent_obs) - self.phy_rs_term(agent_og_obs)

        return r, {"U": no_fin*self.UTILITY_REWARD, "D": no_fail*self.UTILITY_REWARD, "O": 0, "F": F}
    def poolNetStats(self):
        iter = 0
        while self.neighbourMatrix is None or len(self.neighbourMatrix) == 0:
            print(f"Pooling for net stats {iter}")
            self.min_neighbours, self.max_neighbours, self.avg_neighbours, self.neighbourMatrix, self.whichControllersMatrix = self.__get_net_data()
            self.clients_per_node = self._compute_clients_per_node()
            iter += 1
            time.sleep(0.05)

    def _validateAction(self, original_obs, actions):
        failed = {}
        for agent in self.agents:
            if agent not in actions:
                continue
            obs = original_obs[agent]
            Q = obs[STATE_Q_FIELD]
            neighbour = int(actions[agent][ACTION_NEIGHBOUR_IDX_FIELD])
            if len(Q) <= neighbour or neighbour < 0:
                failed[agent] = True
            else:
                failed[agent] = False
        return failed

    def _gen_node_Q_max(self):
        q_list = []
        for idx, no_nodes in enumerate(self.no_nodes_per_layer):
            for i in range(no_nodes):
                q_list.append(self.max_Q_size[idx])

        if self.has_cloud:
            q_list.append(1e6) # Add cloud to basically have infinite Q size.
        return q_list

    def extract_local_data(self, agent_state):
        # Extract the local observation of the agent based on the observation space and the flag setting the variables
        # on the observation space
        observations = {}



        obs = {
            STATE_NODE_ID_FIELD: agent_state[STATE_NODE_ID_FIELD],
            STATE_Q_FIELD: agent_state[STATE_Q_FIELD],
            STATE_FREE_SPACES_FIELD: agent_state[STATE_FREE_SPACES_FIELD],
            STATE_PROCESSING_POWER_FIELD: float(agent_state[STATE_PROCESSING_POWER_FIELD]),
            STATE_QSIZE_FIELD: agent_state[STATE_QSIZE_FIELD],
            STATE_NO_NEIGHBOURS: agent_state[STATE_NO_NEIGHBOURS],

        }

        # Conditionally include based on state_queue_info
        if self.state_info in {"next", "queue_next", "qaggr_next"} and STATE_NEXT_TASK in agent_state:
            obs[STATE_NEXT_TASK] = {
                STATE_TASK_PARAM_PROCESSED_LOCALLY: int(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_PROCESSED_LOCALLY]),
                STATE_TASK_PARAM_PROGRESS: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_PROGRESS]),
                STATE_TASK_PARAM_TOTAL_INSTR: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_TOTAL_INSTR]),
                STATE_TASK_PARAM_INPUT_SIZE: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_INPUT_SIZE]),
                STATE_TASK_PARAM_OUTPUT_SIZE: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_OUTPUT_SIZE]),
            }

        if self.state_info in {"queue", "queue_next"} and "tasks" in agent_state:
            obs[STATE_TASKS_IN_QUEUE] = [
                {
                    STATE_TASK_PARAM_PROCESSED_LOCALLY: int(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_PROCESSED_LOCALLY]),
                    STATE_TASK_PARAM_PROGRESS: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_PROGRESS]),
                    STATE_TASK_PARAM_TOTAL_INSTR: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_TOTAL_INSTR]),
                    STATE_TASK_PARAM_INPUT_SIZE: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_INPUT_SIZE]),
                    STATE_TASK_PARAM_OUTPUT_SIZE: float(agent_state[STATE_NEXT_TASK][STATE_TASK_PARAM_OUTPUT_SIZE]),
                }
                for task in agent_state[STATE_TASKS_IN_QUEUE]
            ]

        if self.state_info in {"qaggr", "qaggr_next"} and "tasks" in agent_state:
            instr_acc = 0
            local_acc = 0
            for task in agent_state[STATE_TASKS_IN_QUEUE]:
                instr_acc += task[STATE_TASK_PARAM_TOTAL_INSTR]
                local_acc += task[STATE_TASK_PARAM_PROCESSED_LOCALLY] * task[STATE_TASK_PARAM_TOTAL_INSTR]
            obs[STATE_TASKQ_AGGR_TOTAL_INSTR] = instr_acc
            obs[STATE_TASKQ_AGGR_TOTAL_LOCAL] = local_acc
        return obs


    def extract_global_data(self, global_obs, dbg_info):
        Q = global_obs[STATE_Q_FIELD]
        layers = global_obs[STATE_G_LAYERS]
        # Check number of overloaded nodes.
        overloaded_nodes = [1 if q >= self.max_Q_size[mq] else 0 for q, mq in zip(Q, layers)]
        # Check percentage of occupancy of the nodes.
        occupancy = [q / self.max_Q_size[mq] for q, mq in zip(Q, layers)]
        # Get the average response time.
        response_time = global_obs[STATE_G_AVERAGE_COMPLETION_TIMES]

        dropped_tasks = global_obs[STATE_G_DROPPED_TASKS]
        finished_tasks = global_obs[STATE_G_FINISHED_TASKS]
        total_tasks = global_obs[STATE_G_TOTAL_TASKS]
        energy_consumed = global_obs[STATE_G_CONSUMED_ENERGY]

        overloaded_nodes_sim = global_obs[STATE_G_OVERLOADED_NODES_SIM]
        dropped_by_expired = global_obs[STATE_G_DROPPED_BY_EXPIRED]
        dropped_on_arrival = global_obs[STATE_G_DROPPED_ON_ARRIVAL]
        total_tasks_received = global_obs[STATE_G_TOTAL_RECEIVED_PER_NODE]
        finished_per_node = global_obs[STATE_G_TOTAL_FINISHED_PER_NODE]

        offloaded_tasks_from_node = global_obs[STATE_G_OFFLOADED_TASKS_FROM_NODE]
        tasks_offloaded_to_node = global_obs[STATE_G_OFFLOADED_TASKS_TO_NODE]

        ids = dbg_info[STATE_G_IDS]
        task_received_since_last_cycle = dbg_info[STATE_G_TASK_RCV_SINCE_LAST_CYCLE]
        tasks_dropped_since_last_cycle = dbg_info[STATE_G_TASKS_DRP_SINCE_LAST_CYCLE]
        average_rt = dbg_info[STATE_G_AVERAGE_RT]

        return overloaded_nodes, occupancy, response_time, dropped_tasks, finished_tasks, total_tasks, energy_consumed, overloaded_nodes_sim, dropped_by_expired, dropped_on_arrival, total_tasks_received, offloaded_tasks_from_node, finished_per_node, tasks_offloaded_to_node, ids, task_received_since_last_cycle, tasks_dropped_since_last_cycle, average_rt



    def set_random_seed(self):
        seed = cg.randomize_seed(self.config_path)
        self.config_archive["random.seed"] = seed

    def get_layer(self, target_of_task):
        acc = 0
        for i in range(len(self.NODES_PER_LAYER)):
            acc += self.NODES_PER_LAYER[i]
            if target_of_task < acc:
                return i
        return -1

    def normalize_observations(self, observations):
        normalized_obs = {}
        for agent in observations.keys():
            obs = observations[agent]
            normalized_obs[agent] = self.normalize_observation(obs)
        return normalized_obs

    def normalize_observation(self, obs, padding=True):
        normalized_obs = {}
        id = obs[STATE_NODE_ID_FIELD]
        normalized_obs[STATE_NODE_ID_FIELD] = id

        # Normalize the queueSize
        queueSize = obs[STATE_QSIZE_FIELD]
        max_Q = self.max_Q_size[self.get_layer(id)]
        queueSize = queueSize / max_Q
        normalized_obs[STATE_QSIZE_FIELD] = queueSize

        # Normalize the processing power
        processingPower = obs[STATE_PROCESSING_POWER_FIELD]
        processingPower = processingPower / self.AVERAGE_PROCESSING_POWER
        normalized_obs[STATE_PROCESSING_POWER_FIELD] = processingPower

        number_of_neighbours = obs[STATE_NO_NEIGHBOURS]
        normalized_obs[STATE_NO_NEIGHBOURS] = int(number_of_neighbours)

        # Normalize the Q
        Q = obs[STATE_Q_FIELD]
        FS = obs[STATE_FREE_SPACES_FIELD]
        normalized_Q = []
        normalized_free_spaces = []
        for i in range(len(Q)):
            neighbor_id = self.neighbourMatrix[id][i]
            neighbor_layer = self.get_layer(neighbor_id)
            n_max_Q = self.max_Q_size[neighbor_layer]
            normalized_Q.append(Q[i] / n_max_Q)
            normalized_free_spaces.append(FS[i] / n_max_Q)
            assert ((n_max_Q - Q[i]) / n_max_Q == FS[i] / n_max_Q,
                    f"Mismatch: Neighbor {neighbor_id} has Q: {Q[i]} and FS: {FS[i]}")
        if padding:
            normalized_Q += [-1 for _ in range(len(Q), self.number_nodes)]
            normalized_free_spaces += [-1 for _ in range(len(Q), self.number_nodes)]
        normalized_obs[STATE_Q_FIELD] = normalized_Q
        normalized_obs[STATE_FREE_SPACES_FIELD] = normalized_free_spaces

        if self.state_info in {"next", "queue_next", "qaggr_next"} and STATE_NEXT_TASK in obs:
            normalized_obs[STATE_NEXT_TASK] = {
                STATE_TASK_PARAM_PROCESSED_LOCALLY: int(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_PROCESSED_LOCALLY]),
                STATE_TASK_PARAM_PROGRESS: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_PROGRESS]),
                STATE_TASK_PARAM_TOTAL_INSTR: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_TOTAL_INSTR])/processingPower,
                STATE_TASK_PARAM_INPUT_SIZE: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_INPUT_SIZE]),
                STATE_TASK_PARAM_OUTPUT_SIZE: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_OUTPUT_SIZE]),
            }
        if self.state_info in {"queue", "queue_next"} and STATE_TASKS_IN_QUEUE in obs:
            normalized_obs[STATE_TASKS_IN_QUEUE] = [
                {
                    STATE_TASK_PARAM_PROCESSED_LOCALLY: int(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_PROCESSED_LOCALLY]),
                    STATE_TASK_PARAM_PROGRESS: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_PROGRESS]),
                    STATE_TASK_PARAM_TOTAL_INSTR: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_TOTAL_INSTR])/processingPower,
                    STATE_TASK_PARAM_INPUT_SIZE: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_INPUT_SIZE]),
                    STATE_TASK_PARAM_OUTPUT_SIZE: float(obs[STATE_NEXT_TASK][STATE_TASK_PARAM_OUTPUT_SIZE]),
                }
                for task in obs[STATE_TASKS_IN_QUEUE]
            ]
        if self.state_info in {"qaggr", "qaggr_next"} and STATE_TASKQ_AGGR_TOTAL_INSTR and STATE_TASKQ_AGGR_TOTAL_LOCAL in obs:
            normalized_obs[STATE_TASKQ_AGGR_TOTAL_INSTR] = obs[STATE_TASKQ_AGGR_TOTAL_INSTR]/processingPower
            normalized_obs[STATE_TASKQ_AGGR_TOTAL_LOCAL] = obs[STATE_TASKQ_AGGR_TOTAL_LOCAL]/processingPower

        return normalized_obs

    def _compute_clients_per_node(self) -> list[int]:
        clients_per_node = []
        for i in range(self.number_nodes):
            clients_count = 0
            for j in self.neighbourMatrix[i]:
                if self.get_layer(j) in self.clients_per_layer:
                    clients_count += 1
            clients_per_node.append(clients_count)
        return clients_per_node

    def __forward_environment(self):
        payload = [
        ]
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_forward_path
        try:
            payload_json = json.dumps(payload)
            # print(payload_json)
            r = requests.post(action_url, payload_json, headers=headers_action, timeout=self.default_timeout)
            r = r.json()
            result = {}  # Should always be empty.
            self._result = result
            return result
        except requests.exceptions.Timeout:
            print("Failed  to send action, could not connect to the environment. Returning old result.")
            return self._result




