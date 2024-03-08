import math
import os
import time
from random import randint
from typing import Callable

import gymnasium
import requests
from gymnasium import Space
from gymnasium.spaces import MultiDiscrete, Dict, Discrete, Box
from pettingzoo import ParallelEnv

import json
import peersim_gym.envs.Utils.PeersimConfigGenerator as cg
from peersim_gym.envs.Utils.PeersimThread import PeersimThread
from peersim_gym.envs.Utils.PeersimVis import PeersimVis

import socket
from contextlib import closing

STATE_NO_NEIGHBOURS = "numberOfNeighbours"

STATE_G_LAYERS = "layers"

STATE_G_OVERLOADED_NODES = "overloadedNodes"

STATE_G_OCCUPANCY = "occupancy"

STATE_G_AVERAGE_COMPLETION_TIMES = "averageCompletionTimes"
STATE_G_Q = "true_Q"
STATE_G_DROPPED_TASKS = "droppedTasks"
STATE_G_FINISHED_TASKS = "finishedTasks"
STATE_G_TOTAL_TASKS = "totalTasks"


AGENT_PREFIX = "worker_"


def not_zero(num):
    return 1 if num > 0 else 0


def average_of_ints_in_string(s):
    '''
    ChatGPT function.
    Computes the average of a string of numbers separated by commas.
    '''
    # Convert the string to a list of integers
    numbers = [int(num) for num in s.strip().split(",")]
    # Compute and return the average
    return sum(numbers) / len(numbers) if numbers else 0


def average_of_floats_in_string(s):
    '''
    ChatGPT function.
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


def can_launch_simulation():
    with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
        return sock.connect_ex(('localhost', 8080)) != 0


class PeersimEnv(ParallelEnv):
    metadata = {"render_modes": ["ansi", "human"], "render_fps": 4}

    def init(self, render_mode=None, configs=None, log_dir=None, randomize_seed=False, phy_rs_term:Callable[[Space], float]=None):
        self.__init__(render_mode, configs, log_dir=log_dir, randomize_seed=randomize_seed, phy_rs_term=phy_rs_term)

    def __init__(self, render_mode=None, simtype="basic", configs=None, log_dir=None, randomize_seed=False,
                 phy_rs_term:Callable[[Space], float]=None):
        # ==== Variables to configure the PeerSim
        # This value does not include the controller, SIZE represents the total number of nodes which includes
        # the controller.
        # (aka if number_nodes is 10 there is a total of 11 nodes (1 controller + 10 workers))

        self.phy_rs_term = phy_rs_term
        validate_simulation_type(simtype)
        self.render_mode = render_mode
        self.randomize_seed = randomize_seed
        if not can_launch_simulation():
            print("Simulation Failed to launch. Port 8080 is taken, please free port 8080 first.")
            exit(1)
        self.number_nodes = -1
        self.max_Q_size = [10, 50]  # TODO this is specified from outside.
        self.max_w = 1

        self.url_api = "http://localhost:8080"
        self.url_action_path = "/action"
        self.url_state_path = "/state"
        self.url_isUp = "/up"
        self.url_isStopped = "/stopped"
        self.url_NetworkData = "/NeighbourData"

        self.default_timeout = 3  # Second

        self.config_archive = configs
        self.simtype = simtype

        self.controllers = self.__gen_config(configs, simtype=simtype)
        self.number_nodes = int(self.config_archive["SIZE"])
        self.possible_agents = [AGENT_PREFIX + str(r) for r in self.controllers]
        self.agent_name_mapping = dict(
            zip(self.possible_agents, self.controllers)
        )
        # ---- State Space
        self.__log_dir = log_dir
        self.__run_counter = 0

        self.no_nodes_per_layer = [int(no_nodes) for no_nodes in
                                   self.config_archive["NO_NODES_PER_LAYERS"].strip().split(",")]

        self.layers_that_get_tasks = [int(layer) for layer in self.config_archive["protocol.clt.layersThatGetTasks"].strip().split(",")]
        if isinstance(self.max_Q_size, list):
            self.q_list = self._gen_node_Q_max()

        else:
            self.q_list = [self.max_Q_size for _ in range(self.number_nodes)]

        self._observation_spaces = {
            agent: Dict(
                {
                    STATE_NODE_ID_FIELD: Discrete(self.number_nodes, start=1),  # Ignores the controller
                    STATE_Q_FIELD: MultiDiscrete(self.q_list[self.agent_name_mapping[agent]]),
                    STATE_FREE_SPACES_FIELD: MultiDiscrete(self.q_list[self.agent_name_mapping[agent]]),
                    STATE_PROCESSING_POWER_FIELD: Box(high=self.max_w, low=0, dtype=float)
                }
            ) for agent in self.possible_agents
        }

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
        # self.simulator = PeersimThread(name='Run0', configs=self.config_path)
        # self.simulator.run()
        self.UTILITY_REWARD = self.config_archive["utility_reward"]
        self.DELAY_WEIGHT = self.config_archive["delay_weight"]
        self.OVERLOAD_WEIGHT = self.config_archive["overload_weight"]
        self.TRANSMISSION_POWER = float(self.config_archive["protocol.props.P_ti"])
        self.PATH_LOSS_CONSTANT = float(self.config_archive["protocol.props.Beta1"])
        self.PATH_LOSS_EXPONENT = float(self.config_archive["protocol.props.Beta2"])
        self.BANDWIDTH = float(self.config_archive["protocol.props.B"])
        self.NORMALIZED_THERMAL_NOISE_POWER = -174
        self.AVERAGE_TASK_SIZE, self.AVERAGE_TASK_INSTR, self.TASK_ARRIVAL_RATE = self._compute_avg_task_data()
        self.AVERAGE_PROCESSING_POWER, self.AVERAGE_MAX_Q, self.PROCESSING_POWERS = self._compute_worker_data()
        self.NODES_PER_LAYER = [int(no_nodes) for no_nodes in self.config_archive["NO_NODES_PER_LAYERS"].strip().split(",")]
        self.avg_neighbours = -1
        self.min_neighbours = -1
        self.max_neighbours = -1
        self.neighbourMatrix = []
        self.clients_per_node = []
        self.clients_per_layer = [int(no_nodes) for no_nodes in self.config_archive["clientLayers"].strip().split(",")]
        self.has_cloud = int(self.config_archive["CLOUD_EXISTS"])

        self.last_reward_components = {}

        if self.render_mode == "human":
            self.vis = PeersimVis(self.has_cloud)

    def observation_space(self, agent):
        return Dict(
            {
                STATE_NODE_ID_FIELD: Discrete(self.number_nodes, start=1),  # Ignores the controller
                STATE_Q_FIELD: MultiDiscrete(self.q_list),
                STATE_FREE_SPACES_FIELD: MultiDiscrete(self.q_list),
                STATE_PROCESSING_POWER_FIELD: Box(high=self.max_w, low=0, dtype=float)
            }
        )

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
        observations = self.normalize_observations(observations)

        infos = {agent: {} for agent in self.agents}

        return observations, infos

    def step(self, actions):
        if not self.__is_up() or not actions:
            self.agents = []
            return {}, {}, {}, {}, {}
        original_obs = self.state
        mask = self._validateAction(original_obs, actions)
        result = self.__send_action(actions)

        # Wait for the simulation to stabilize so the next state is actually the next state the agent would observe.
        while not self.__is_stopped():
            time.sleep(0.05)

        observations, done, info = self.__get_obs()

        rewards = self._compute_rewards(original_obs, observations, actions, result, mask)

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
        self.vis.update_state(self._global_obs, self.max_Q_size, self.neighbourMatrix, self.last_actions, self.controllers, self.agent_name_mapping, self._result)
        self.vis.draw()  # TODO Project the coordinates to the render space...

    def close(self):
        self.simulator.stop()

    def __gen_config(self, configs, simtype, regen_seed=False):
        controller = []
        # Checking the configurations
        if configs is None:

            configs = {"Q_MAX": str(self.max_Q_size), "SIZE": str(self.number_nodes),
                       "random.seed": self.__gen_seed()}

            controller, self.config_path = cg.generate_config_file(configs, simtype)
        elif type(configs) is dict:
            # Consistency of QMax in configs and the arguments.
            if "Q_MAX" in configs:
                self.max_Q_size = [int(q) for q in configs["Q_MAX"].strip().split(",")]

            if "SIZE" in configs:
                self.number_nodes = int(configs["SIZE"])  # - 1 Not anymore

            if not ("random.seed" in configs) or regen_seed:
                configs["random.seed"] = self.__gen_seed()
                print(f'seed:{configs["random.seed"]}')

            controller, self.config_path = cg.generate_config_file(configs, simtype)
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
                os.remove(log_file)
        self.simulator.run(output_file=log_file)

    def __get_obs(self):
        # Retrieve Observation
        space_url = self.url_api + self.url_state_path
        headers_state = {"Accept": "application/json", "Connection": "keep-alive"}
        try:
            iter = 0
            r = requests.get(space_url, headers=headers_state, timeout=self.default_timeout)
            while r.status_code < 200 or r.status_code >= 300: # Most likely only 200 will be returned, but I'm paranoid
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

            observations = {self.agents[i]: partial_obs[i] for i in range(len(self.agents))}
            self.state = observations
            extracted_data = self.extract_global_data(global_obs)
            dbg_info = {
                STATE_G_Q: global_obs[STATE_Q_FIELD],
                STATE_G_OVERLOADED_NODES: extracted_data[0],
                STATE_G_OCCUPANCY: extracted_data[1],
                STATE_G_AVERAGE_COMPLETION_TIMES: extracted_data[2],
                STATE_G_DROPPED_TASKS: extracted_data[3],
                STATE_G_FINISHED_TASKS: extracted_data[4],
                STATE_G_TOTAL_TASKS: extracted_data[5]
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
            } for name in self.agent_name_mapping.keys()
        ]
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_action_path
        try:
            payload_json = json.dumps(payload)
            # print(payload_json)
            r = requests.post(action_url, payload_json, headers=headers_action, timeout=self.default_timeout).json()
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
        return min, max, avg, neighbourMatrix

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

    def __gen_seed(self):
        n = randint(1000000000, 9999999999)
        return str(n)

    def _compute_rewards(self, original_obs, obs, actions, result, mask) -> dict[float]:
        rewards = {}
        for agent in self.agents:
            if agent in actions and not mask[agent]:
                p = self._compute_agent_reward(
                    agent_og_obs=original_obs[agent],
                    agent_obs=obs[agent],
                    action=actions[agent],
                    agent_result=result[agent],
                    agent_idx=self.agent_name_mapping[agent]
                )
                rewards[agent], self.last_reward_components[agent] = p
            elif mask[agent]:
                rewards[agent] = -self.UTILITY_REWARD
            else:
                print(f"Action of agent {agent} was not found in the actions sent.")
        return rewards

    def _compute_delay(self, d_i_j, w_o):
        if d_i_j == 0:
            t_c = 0
        else:
            T = self.AVERAGE_TASK_SIZE * 8e6;
            W = self.BANDWIDTH * 1e6;
            lambda_wavelength = 3e8 / 2.4e9
            P_t = self.TRANSMISSION_POWER
            N_0 = self.NORMALIZED_THERMAL_NOISE_POWER + 10 * math.log10(W)
            h = 10 * math.log10((lambda_wavelength**2) / (16 * math.pi**2)) - 20 * math.log10(d_i_j)

            SNR_db = P_t + h - N_0
            SNR_linear = 10 ** (SNR_db / 10)
            C = W * math.log2(1 + SNR_linear)

            t_c = w_o * T / C
        return t_c

    def _compute_agent_reward(self, agent_og_obs, agent_obs, action, agent_result, agent_idx):
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
        if int(len(source_node_og_info["Q"])) < int(target_of_task_neighbourhood_index) or int(target_of_task_neighbourhood_index) < 0:
            return -self.UTILITY_REWARD, {"U": -self.UTILITY_REWARD, "D": 0, "O": 0}

        target_layer = self.get_layer(target_of_task_global_index)
        target_processing_power = self.PROCESSING_POWERS[target_layer]
        target_max_q = self.max_Q_size[target_layer]

        if self.neighbourMatrix is None or len(self.neighbourMatrix) == 0:
            print("Neighbour Matrix is empty or none. Setting ranks to 1")
            source_rank = 1
            target_rank = 1
        else:
            source_rank = self.clients_per_node[source_of_task_global_index] if self.get_layer(source_of_task_global_index) in self.layers_that_get_tasks else 0
            target_rank = self.clients_per_node[target_of_task_global_index] if self.get_layer(target_of_task_global_index)  in self.layers_that_get_tasks else 0

        source_layer = self.get_layer(source_of_task_global_index)
        source_processing_power = self.PROCESSING_POWERS[source_layer]
        source_max_q = self.max_Q_size[source_layer]

        q_l = source_node_og_info["queueSize"]
        q_o = target_node_worker_info["queueSize"]

        source_var = self.TASK_ARRIVAL_RATE * source_rank - source_processing_power/self.AVERAGE_TASK_INSTR
        target_var = self.TASK_ARRIVAL_RATE * target_rank - target_processing_power/self.AVERAGE_TASK_INSTR
        q_expected_l = q_l if locally_processed else max(q_l - 1, 0)
        q_expected_o = q_o if locally_processed else q_o + 1

        q_expected_l = max(min(q_expected_l + source_var, source_max_q), 0)
        q_expected_o = max(min(q_expected_o + target_var, target_max_q), 0)

        d_i_j = agent_result[RESULT_DISTANCE_FIELD]

        w_o = 1 if not locally_processed and 0 < q_l - 1 else 0
        w_l = 1 if locally_processed else 0

        if w_l == 0 and w_o == 0: # queue is empty, nothing to do, no penalty or reward given.
            print("Empty queue. No action taken.")
            return self.UTILITY_REWARD, {"U": self.UTILITY_REWARD/2, "D": 0, "O": 0} # TODO make this maximum reward, as the action itself is not something bad.

        miu_l = source_processing_power
        miu_o = target_processing_power

        # Compute Utility:
        U = self.UTILITY_REWARD # * math.log(1 + w_l + w_o)

        # Compute Delay

        t_w = not_zero(w_l) * (q_l / miu_l) + not_zero(w_o) * ((q_l / miu_l) + (q_o / miu_o))  # q_l/miu_l on the second term represents the time spent waiting in queue before being selected for offloading
        t_c = self._compute_delay(d_i_j, w_o)

        # og: t_e = self.AVERAGE_TASK_INSTR * (w_l / source_processing_power + w_o / target_processing_power)
        t_e = self.AVERAGE_TASK_INSTR / target_processing_power - self.AVERAGE_TASK_INSTR / source_processing_power

        t_w = min(t_w, self.UTILITY_REWARD * self.DELAY_WEIGHT["queue"])
        t_e = min(t_e, self.UTILITY_REWARD * self.DELAY_WEIGHT["exec"])
        t_c = min(t_c, self.UTILITY_REWARD * self.DELAY_WEIGHT["comm"])

        D =  t_w + t_c + t_e # / (w_l + w_o)

        # Compute Overload
        # q_prime_l = q_expected_l  # min(max(0, q_l - avg_tasks_processed_per_node) + w_l, self.AVERAGE_MAX_Q)
        # q_prime_o = q_expected_o  # min(max(0, q_o - avg_tasks_processed_per_node) + w_o, self.AVERAGE_MAX_Q)
        # p_overload_l = max(0.0, self.TASK_ARRIVAL_RATE - q_prime_l)
        # p_overload_o = max(0.0, self.TASK_ARRIVAL_RATE - q_prime_o)

        distance_to_Ovl_l = max((source_max_q - q_expected_l) / source_max_q, 0.001)  # Normalized manhattan distance
        distance_to_Ovl_o = max((target_max_q - q_expected_o) / target_max_q, 0.001)  # 0.0001 is to avoid log(0)
        O = -math.log10(w_l * distance_to_Ovl_l + w_o * distance_to_Ovl_o)  # we subtract O, therfore the minus
                                                                                  # Was using the ln before, now using log

        # Capping the percentages to be between 100 and -100
        # U = max(min(U, self.UTILITY_REWARD), self.UTILITY_REWARD) / self.UTILITY_REWARD
        # Some people call this cheating, I call it not despairing -, _ ,-.

        O = O/3 * self.UTILITY_REWARD * self.OVERLOAD_WEIGHT # I cap the delay distance at 0.001 (0.1% to overload) therefore the log will only go down to 3

        # computing reward and normalizing it
        reward = U - (D + O)
        F = 0
        if self.phy_rs_term is not None:
            F = self.phy_rs_term(agent_obs) - self.phy_rs_term(agent_og_obs)
        reward += F
        print(f"Action:{source_of_task_global_index}->{target_of_task_global_index}         Reward: U:{U} | D:{D} [t_C {t_c} ; t_w {t_w} ; t_e {t_e}] | O:{O}) | F:{F}")
        return (reward, {"U": U, "D": D, "O": O, "F": F})

    def _compute_avg_task_data(self):
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
            acc_task_instr = (float(task_instr[i]) + float(task_instr_size[i])) * int(task_weights[i])
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

    def poolNetStats(self):
        iter = 0
        while self.neighbourMatrix is None or len(self.neighbourMatrix) == 0:
            print(f"Pooling for net stats {iter}")
            self.min_neighbours, self.max_neighbours, self.avg_neighbours, self.neighbourMatrix = self.__get_net_data()
            self.clients_per_node = self._compute_clients_per_node()
            iter += 1
            time.sleep(0.05)

    def _validateAction(self, original_obs, actions):
        failed = {}
        for agent in self.agents:
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
        return q_list

    def extract_global_data(self, global_obs):
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

        return overloaded_nodes, occupancy, response_time, dropped_tasks, finished_tasks, total_tasks

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
            assert ((n_max_Q - Q[i]) / n_max_Q == FS[i] / n_max_Q, f"Mismatch: Neighbor {neighbor_id} has Q: {Q[i]} and FS: {FS[i]}")
        if padding:
            normalized_Q += [-1 for _ in range(len(Q), self.number_nodes)]
            normalized_free_spaces += [-1 for _ in range(len(Q), self.number_nodes)]
        normalized_obs[STATE_Q_FIELD] = normalized_Q
        normalized_obs[STATE_FREE_SPACES_FIELD] = normalized_free_spaces
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