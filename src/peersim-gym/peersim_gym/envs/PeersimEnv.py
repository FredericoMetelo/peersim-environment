import math
import os
import time
import time
from random import randint
from typing import List

import gymnasium as gym
import numpy as np
import requests
from gymnasium.core import ActType
from gymnasium.spaces import MultiDiscrete, Dict, Discrete, Box
from pettingzoo import ParallelEnv

import peersim_gym.envs.PeersimConfigGenerator as cg
from peersim_gym.envs.PeersimThread import PeersimThread


AGENT_PREFIX = "worker_"


def not_zero(num):
    return 1 if num > 0 else 0


class PeersimEnv(ParallelEnv):
    metadata = {"render_modes": ["ansi"], "render_fps": 4}

    def init(self, render_mode=None, configs=None, log_dir=None):
        self.__init__(render_mode, configs, log_dir=log_dir)

    def __init__(self, render_mode=None, configs=None, log_dir=None):
        # ==== Variables to configure the PeerSim
        # This value does not include the controller, SIZE represents the total number of nodes which includes
        # the controller.
        # (aka if number_nodes is 10 there is a total of 11 nodes (1 controller + 10 workers))

        self.ACTION_NEIGHBOUR_IDX_FIELD = "neighbourIndex"
        self.ACTION_HANDLER_ID_FIELD = "controllerId"
        self.RESULT_DISTANCE_FIELD = "distance"
        self.RESULT_WORKER_INFO_FIELD = "wi"
        self.STATE_NODE_ID_FIELD = "n_i"
        self.STATE_Q_FIELD = "Q"
        self.STATE_PROCESSING_POWER_FIELD = "w"

        self.number_nodes = 10
        self.max_Q_size = 10
        self.max_w = 1

        self.url_api = "http://localhost:8080"
        self.url_action_path = "/action"
        self.url_state_path = "/state"
        self.url_isUp = "/up"

        self.default_timeout = 3  # Second

        self.config_archive = configs

        controllers = self.__gen_config(configs)
        self.possible_agents = [AGENT_PREFIX + str(r) for r in controllers]
        self.agent_name_mapping = dict(
            zip(self.possible_agents, list(range(len(self.possible_agents))))
        )
        # ---- State Space
        self.__log_dir = log_dir
        self.__run_counter = 0

        if isinstance(self.max_Q_size, list):

            if len(self.max_Q_size) != self.number_nodes:
                print("The number of entries in max_Q_size needs to be equal to"
                      " the number of nodes. Or then max_Q_size needs to be just a number")
                return
            self.q_list = self.max_Q_size
            raise Exception("Nodes with diferent max lenghts is not supported yet!")  # TODO rectify this!!
        else:
            self.q_list = [self.max_Q_size for _ in range(self.number_nodes)]

        self._observation_spaces = {
            agent: Dict(
                {
                    self.STATE_NODE_ID_FIELD: Discrete(self.number_nodes, start=1),  # Ignores the controller
                    self.STATE_Q_FIELD: MultiDiscrete(self.q_list),
                    self.STATE_PROCESSING_POWER_FIELD: Box(high=self.max_w, low=0, dtype=float)
                }
            ) for agent in self.possible_agents
        }

        # ---- Action Space
        self._action_spaces = {
            agent: Dict(
                {
                    self.ACTION_HANDLER_ID_FIELD: Discrete(self.number_nodes - 1, start=1),
                    self.ACTION_NEIGHBOUR_IDX_FIELD: Discrete(self.max_Q_size, start=1)
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
        self.UTILITY_REWARD = float(self.config_archive["protocol.mng.r_u"])
        self.DELAY_WEIGHT = float(self.config_archive["protocol.mng.X_d"])
        self.OVERLOAD_WEIGHT = float(self.config_archive["protocol.mng.X_o"])
        self.TRANSMISSION_POWER = float(self.config_archive["protocol.props.P_ti"])
        self.PATH_LOSS_CONSTANT = float(self.config_archive["protocol.props.Beta1"])
        self.PATH_LOSS_EXPONENT = float(self.config_archive["protocol.props.Beta2"])
        self.BANDWIDTH = float(self.config_archive["protocol.props.B"])
        self.NORMALIZED_THERMAL_NOISE_POWER = 174
        self.AVERAGE_TASK_SIZE, self.AVERAGE_TASK_INSTR, self.TASK_ARRIVAL_RATE = self._compute_avg_task_data()
        self.AVERAGE_PROCESSING_POWER, self.AVERAGE_MAX_Q = self._compute_average_worker_data()

    def observation_space(self, agent):
        return Dict(
            {
                self.STATE_NODE_ID_FIELD: Discrete(self.number_nodes, start=1),  # Ignores the controller
                self.STATE_Q_FIELD: MultiDiscrete(self.q_list),
                self.STATE_PROCESSING_POWER_FIELD: Box(high=self.max_w, low=0, dtype=float)
            }
        )

    def action_space(self, agent):
        return Dict(
            {
                self.ACTION_HANDLER_ID_FIELD: Discrete(self.number_nodes - 1, start=1),
                self.ACTION_NEIGHBOUR_IDX_FIELD: Discrete(self.max_Q_size, start=1)
            }
        )

    def reset(self, **kwargs):

        self.agents = self.possible_agents[:]
        self.num_moves = 0

        if self.simulator != None:
            self.simulator.stop()
        else:
            self.simulator = PeersimThread(name=f'Run{self.__run_counter}', configs=self.config_path)
        # self.__gen_config(self.config_archive, regen_seed=True)
        self.__run_peersim()
        while not self.__is_up():
            time.sleep(0.5)  # Good Solution? No... But it is what it is.
        print("Server is up")

        observations, done, info = self.__get_obs()
        infos = {agent: {} for agent in self.agents}

        return observations, infos

    def step(self, actions):
        if not self.__is_up() or not actions:
            self.agents = []
            return {}, {}, {}, {}, {}
        original_obs = self.state
        mask = self._validateAction(original_obs, actions)
        result = self.__send_action(actions)
        observations, done, info = self.__get_obs()

        rewards = self._compute_rewards(original_obs, observations, actions, result, mask)

        terminations = {agent: done for agent in self.agents}
        self.num_moves += 1
        truncations = {agent: False for agent in self.agents}

        return observations, rewards, terminations, truncations, info

    def render(self):
        pass

    def close(self):
        self.simulator.stop()

    def __gen_config(self, configs, regen_seed=False):
        controller = []
        # Checking the configurations
        if configs is None:

            configs = {"protocol.wrk.Q_MAX": str(self.max_Q_size), "SIZE": str(self.number_nodes),
                       "random.seed": self.__gen_seed()}

            controller = cg.generate_config_file(configs)
            self.config_path = cg.TARGET_FILE_PATH

        elif type(configs) is dict:
            # Consistency of QMax in configs and the arguments.
            if "protocol.wrk.Q_MAX" in configs:
                self.max_Q_size = int(configs["protocol.wrk.Q_MAX"])

            if "SIZE" in configs:
                self.number_nodes = int(configs["SIZE"])  # - 1 Not anymore

            if not ("random.seed" in configs) or regen_seed:
                configs["random.seed"] = self.__gen_seed()
                print(f'seed:{configs["random.seed"]}')

            controller = cg.generate_config_file(configs)
            self.config_path = cg.TARGET_FILE_PATH
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
            s = requests.get(space_url, headers=headers_state, timeout=self.default_timeout).json()
            obs = s.get('state')
            done = s.get("done")
            self._observation = obs
            self._done = done

            observations = {self.agents[i]: obs[i] for i in range(len(self.agents))}
            self.state = observations

            dbg_info = s.get("info")
            self._info = dbg_info
            return observations, done, dbg_info
        except requests.exceptions.Timeout:
            print("Failed to get observation, connection timed out. Returning old reward.")
            return self._observation, True, self._info

    def __send_action(self, action):

        payload = [{self.ACTION_NEIGHBOUR_IDX_FIELD: int(action.get(name).get(self.ACTION_NEIGHBOUR_IDX_FIELD)),
                    self.ACTION_HANDLER_ID_FIELD: int(self.agent_name_mapping.get(name))} for name in
                   self.agent_name_mapping.keys()]
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_action_path
        try:
            r = requests.post(action_url, json=payload, headers=headers_action, timeout=self.default_timeout).json()
            result = {AGENT_PREFIX + str(reward["srcId"]): reward for reward in r}
            self._result = result
            return result
        except requests.exceptions.Timeout:
            print("Failed to send action, could not connect to the environment. Returning old result.")
            return self._result

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

    def __gen_seed(self):
        n = randint(1000000000, 9999999999)
        return str(n)

    def _compute_rewards(self, original_obs, obs, actions, result, mask) -> dict[float]:
        rewards = {}
        for agent in self.agents:
            if agent in actions and not mask[agent]:
                rewards[agent] = self._compute_agent_reward(
                    agent_og_obs=original_obs[agent],
                    agent_obs=obs[agent],
                    action=actions[agent],
                    agent_result=result[agent]
                )
            elif mask[agent]:
                rewards[agent] = -self.UTILITY_REWARD
            else:
                print(f"Action of agent {agent} was not found in the actions sent.")
        return rewards

    def _compute_agent_reward(self, agent_og_obs, agent_obs, action, agent_result):
        # Prepare data

        source_of_task = action[self.ACTION_HANDLER_ID_FIELD]
        target_of_task = action[self.ACTION_NEIGHBOUR_IDX_FIELD]

        source_node_og_info = agent_og_obs
        source_node_info = agent_obs
        target_node_worker_info = agent_result[self.RESULT_WORKER_INFO_FIELD]
        locally_processed = agent_obs[self.STATE_NODE_ID_FIELD] == target_node_worker_info["id"]

        if int(target_node_worker_info["queueSize"]) < int(target_of_task) or int(target_of_task) < 0:
            return -self.UTILITY_REWARD

        q_l = len(source_node_og_info[self.STATE_Q_FIELD])
        q_o = target_node_worker_info["queueSize"]
        q_expected_l = len(source_node_info[self.STATE_Q_FIELD])
        q_expected_o = q_o if locally_processed else q_o + 1  # Change to W_o for allowing multiple offloads

        d_i_j = agent_result[self.RESULT_DISTANCE_FIELD]

        w_o = 1  # Number of tasks offloaded is always 1
        w_l = q_l - w_o if 0 < q_l - w_o else 0

        miu_l = source_node_info[self.STATE_PROCESSING_POWER_FIELD]
        miu_o = target_node_worker_info["nodeProcessingPower"]

        # Compute Utility:
        U = self.UTILITY_REWARD * math.log(1 + w_l + w_o)

        # Compute Delay
        t_w = not_zero(w_l) * (q_l / miu_l) + not_zero(w_o) * ((q_l / miu_l) + (q_o / miu_o))
        r_i_j = self.BANDWIDTH * math.log(
            1 + (self.PATH_LOSS_CONSTANT * math.pow(d_i_j, self.PATH_LOSS_EXPONENT) * self.TRANSMISSION_POWER) / (
                        self.BANDWIDTH * self.NORMALIZED_THERMAL_NOISE_POWER))
        t_c = 2 * w_o * self.AVERAGE_TASK_SIZE / r_i_j if d_i_j != 0 else 0
        t_e = self.AVERAGE_TASK_INSTR * (w_l / self.AVERAGE_PROCESSING_POWER + w_o / self.AVERAGE_PROCESSING_POWER)
        D = self.DELAY_WEIGHT * (t_w + t_c + t_e) / (w_l + w_o)

        # Compute Overload
        q_prime_l = min(max(0, q_expected_l - self.AVERAGE_PROCESSING_POWER) + w_l, self.AVERAGE_MAX_Q)
        q_prime_o = min(max(0, q_expected_o - self.AVERAGE_PROCESSING_POWER) + w_o, self.AVERAGE_MAX_Q)
        p_overload_l = max(0.0, self.TASK_ARRIVAL_RATE - q_prime_l)
        p_overload_o = max(0.0, self.TASK_ARRIVAL_RATE - q_prime_o)

        O = self.OVERLOAD_WEIGHT * (w_l * p_overload_l + w_o * p_overload_o) / (
                    w_l + w_o) if w_l != 0 and w_o != 0 else 0
        return U - (D + O)

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

    def _compute_average_worker_data(self):
        average_frequency = self.config_archive["protocol.wrk.FREQ"]
        average_no_cores = self.config_archive["protocol.wrk.NO_CORES"]
        average_max_Q = self.config_archive["protocol.wrk.Q_MAX"]
        return float(average_no_cores) * float(average_frequency), int(average_max_Q)

    def _validateAction(self, original_obs, actions):
        failed = {}

        for agent in self.agents:
            obs = original_obs[agent]
            Q = obs[self.STATE_Q_FIELD]
            neighbour = int(actions[agent][self.ACTION_NEIGHBOUR_IDX_FIELD])
            if len(Q) <= neighbour or neighbour < 0:
                failed[agent] = True
            else:
                failed[agent] = False
        return failed
