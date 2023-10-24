import os
import time
import time
from random import randint

import gymnasium as gym
import numpy as np
import requests
from gymnasium.core import ActType
from gymnasium.spaces import MultiDiscrete, Dict, Discrete, Box

import peersim_gym.envs.PeersimConfigGenerator as cg
from peersim_gym.envs.PeersimThread import PeersimThread


class PeersimEnv(gym.Env):
    metadata = {"render_modes": ["ansi"], "render_fps": 4}

    def __init__(self, render_mode=None, configs=None, log_dir=None):
        # ==== Variables to configure the PeerSim
        # This value does not include the controller, SIZE represents the total number of nodes which includes
        # the controller.
        # (aka if number_nodes is 10 there is a total of 11 nodes (1 controller + 10 workers))
        self.number_nodes = 10
        self.max_Q_size = 10
        self.max_w = 1

        self.url_api = "http://localhost:8080"
        self.url_action_path = "/action"
        self.url_state_path = "/state"
        self.url_isUp = "/up"

        self.default_timeout = 3  # Second

        self.config_archive = configs

        self.__gen_config(configs)

        # ==== Environment Definition
        # ---- State Space
        self.__log_dir = log_dir
        self.__run_counter = 0

        if isinstance(self.max_Q_size, list):

            if len(self.max_Q_size) != self.number_nodes:
                print("The number of entries in max_Q_size needs to be equal to"
                      " the number of nodes. Or then max_Q_size needs to be just a number")
                return
            q_list = self.max_Q_size
            raise Exception("Nodes with diferent max lenghts is not supported yet!")  # TODO rectify this!!
        else:
            q_list = [self.max_Q_size for _ in range(self.number_nodes)]
        self.observation_space = Dict(
            {
                "n_i": Discrete(self.number_nodes, start=1),  # Ignores the controller
                "Q": MultiDiscrete(q_list),
                "w": Box(high=self.max_w, low=0, dtype=float)
                # The authors use a Natural number to represent this, value. I use a continuous value, because the way
                # I interpreted the W is how many tasks are bing processed by the node in one unit of time.
                # I assume that 100 ticks makes one unit of time (100 ticks is the time it takes for all nodes to take
                # one step. In 100 ticks depending on task size, it's possible that no tasks finish, having value bellow
                # 1. therefore it makes sense for this value to be continuous and varying between 0 and max_w.
            }
        )

        # ---- Action Space
        self.action_space = Dict(
            {
                "target_node": Discrete(self.number_nodes - 1, start=1),
                "offload_amount": Discrete(self.max_Q_size, start=1)
            }
        )

        # mvn spring-boot:run -Dspring-boot.run.arguments=configs/default-config.txt
        # Run the actual environment.
        # Prepare the Configuration file

        # with subprocess as s:
        self.simulator = None
        # self.simulator = PeersimThread(name='Run0', configs=self.config_path)
        # self.simulator.run()

    def __gen_config(self, configs, regen_seed=False):
        # Checking the configurations
        if configs is None:

            configs = {"protocol.wrk.Q_MAX": str(self.max_Q_size), "SIZE": str(self.number_nodes),
                       "random.seed": self.__gen_seed()}

            cg.generate_config_file(configs)
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

            cg.generate_config_file(configs)
            self.config_path = cg.TARGET_FILE_PATH
        elif type(configs) is str:
            self.config_path = configs

        else:
            raise Exception(
                "Invalid Type for the configs parameter. Needs to be None, a Dictionary or a String. Please see the project README.md")

    def init(self, render_mode=None, configs=None, log_dir=None):
        self.__init__(render_mode, configs, log_dir=log_dir)

    def reset(self, **kwargs):
        if self.simulator != None:
            self.simulator.stop()
        else:
            self.simulator = PeersimThread(name=f'Run{self.__run_counter}', configs=self.config_path)
        self.__gen_config(self.config_archive, regen_seed=True)
        self.__run_peersim()
        while not self.__is_up():
            time.sleep(0.5)  # Good Solution? No... But it is what it is.
        print("Server is up")
        obs, done, info = self.__get_obs()
        return obs, info

    def step(self, action: ActType):
        # A step will advance the simulation in 100 ticks
        # Sends an action, first tests if the simulation is still running if the simulation has already
        # stopped then returns the last observed state.Otherwise, returns the result of taking the step.

        if not self.__is_up():
            return self._observation, 0, True, False, self._info

        r = self.__send_action(action)
        reward_for_action = float(r.content)  # Gives NaN sometimes. How to deal with it? Find a division by 0?\

        self.__print_information(action, reward_for_action)

        obs, done, info = self.__get_obs()

        return obs, reward_for_action, done, False, info

    def render(self):
        pass

    def close(self):
        self.simulator.stop()

    def observation_space_shape(self):
        return self.observation_space.sample().shape()

    def action_space_shape(self):
        return self.action_space.sample().shape()

    def __see_types(self):
        print("Example of action.")
        print(self.action_space.sample())
        print("Example of space.")
        print(self.observation_space.sample())

    def __print_information(self, action, reward_for_action):
        print(self._observation['Q'])
        tasks_target = self._observation['Q'][int(action["target_node"]) - 1] # Q is between 0 and N-1, and the node indexes are between 1 and N
        O_t = "" if tasks_target < self.max_Q_size else " (O)"
        tasks_source = self._observation['Q'][int(self._observation['n_i']) - 1]
        O_s = "" if tasks_source < self.max_Q_size else " (O)"
        message = "Offload < from:" + str(self._observation['n_i']) + " {Q:" + str(tasks_source) + O_s + \
                  "} to:" + str(action["target_node"]) + " {Q:" + str(tasks_target) + O_t + \
                  "} no_tasks:" + str(action["offload_amount"]) + "}> -> Reward:" + str(reward_for_action)
        print(message)

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
            obs = {'n_i': s.get("state").get('n_i'),
                   'Q': np.asarray(s.get("state").get("Q")),
                   'w': np.asarray([s.get("state").get("w")])
                   }
            # print(obs)
            done = s.get("done")
            self._observation = obs
            self._done = done

            dbg_info = s.get("info")
            self._info = dbg_info
            return obs, done, dbg_info
        except requests.exceptions.Timeout:
            print("Failed to get observation, connection timed out. Returning old reward.")
            return self._observation, True, self._info

    def __send_action(self, action):
        payload = {"neighbourIndex": action["target_node"], "noTasks": action["offload_amount"]}
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_action_path
        try:
            r = requests.post(action_url, json=payload, headers=headers_action, timeout=self.default_timeout)
            self._reward = r
            return r
        except requests.exceptions.Timeout:
            print("Failed to send action, could not connect to the environment. Returning old reward.")
            return self._reward

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