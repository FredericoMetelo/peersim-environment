import subprocess
from typing import Optional, Union, List, Tuple
import subprocess as s
import logging
import threading
import time
import gym
import requests
from gym.spaces import MultiDiscrete, Dict, Discrete, Box
import numpy as np
from gym.core import RenderFrame, ActType, ObsType

from peersim_gym.envs.PeersimThread import PeersimThread
import peersim_gym.envs.PeersimConfigGenerator as cg


class PeersimEnv(gym.Env):
    metadata = {"render_modes": ["ansi"], "render_fps": 4}

    def __init__(self, render_mode=None, number_nodes=10, max_Q_size=10, max_w=1, configs=None):
        # ==== Variables to configure the PeerSim
        self.number_nodes = number_nodes
        self.max_Q_size = max_Q_size
        self.max_w = max_w

        self.url_api = "http://localhost:8080"
        self.url_action_path = "/action"
        self.url_state_path = "/state"
        self.url_isUp = "/up"

        # ==== Environment Definition
        # ---- State Space
        self.__run_counter = 0
        if isinstance(max_Q_size, list):
            if len(max_Q_size) != number_nodes:
                print("The number of entries in max_Q_size needs to be equal to"
                      " the number of nodes. Or then max_Q_size needs to be just a number")
                return
            q_list = max_Q_size
        else:
            q_list = [max_Q_size for _ in range(number_nodes)]
        self.observation_space = Dict(
            {
                "n_i": Discrete(number_nodes, start=1),
                "Q": MultiDiscrete(q_list),
                "w": Box(high=max_w, low=0, dtype=np.float)
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
                "target_node": Discrete(number_nodes-1, start=1),
                "offload_amount": Discrete(max_Q_size, start=1)
            }
        )

        # mvn spring-boot:run -Dspring-boot.run.arguments=configs/default-config.txt
        # Run the actual environment.
        # Prepare the Configuration file

        if configs is None or type(configs) is dict:
            # Consistency of QMax in configs and the arguments.
            if configs is not None and "protocol.wrk.Q_MAX" in configs:
                if configs["protocol.wrk.Q_MAX"] != str(self.max_Q_size):
                    raise Exception("Argument QMAX and the configuration protocol.wrk.Q_MAX in the configuration "
                                    "dictionary must be equal. Please specify the QMAX parameter.")
            elif configs is not None:
                configs["protocol.wrk.Q_MAX"] = str(self.max_Q_size)
            else:
                # configs is None
                configs = {"protocol.wrk.Q_MAX": str(self.max_Q_size)}

            cg.generate_config_file(configs)
            self.config_path = cg.TARGET_FILE_PATH

        elif type(configs) is str:
            self.config_path = configs
        else:
            raise Exception(
                "Invalid Type for the configs parameter. Needs to be None, a Dictionary or a String. Please see the project README.md")
        # with subprocess as s:
        self.simulator = None
        # self.simulator = PeersimThread(name='Run0', configs=self.config_path)
        # self.simulator.run()

    def init(self, render_mode=None, number_nodes=10, max_Q_size=10, max_w=1, configs=None):
        self.__init__(render_mode, number_nodes, max_Q_size, max_w, configs)

    def reset(self, **kwargs):
        if self.simulator != None:
            self.simulator.stop()
        else:
            self.simulator = PeersimThread(name=f'Run{self.__run_counter}', configs=self.config_path)
        self.__run_peersim()
        while not self.__is_up():
            time.sleep(0.5)  # Good Solution? No... But it is what it is.
        print("Server is up")
        obs, done = self._get_obs()
        return obs, {}

    def step(self, action: ActType):
        # A step will advance the simulation in 100 ticks
        # Send action.
        r = self._send_action(action)
        reward_for_action = float(r.content)  # Gives NaN sometimes. How to deal with it? Find a division by 0?
        print(reward_for_action)
        obs, done = self._get_obs()
        return obs, reward_for_action, done, False, {}

    def render(self):
        pass

    def close(self):
        self.simulator.stop()

    def __see_types(self):
        print("Example of action.")
        print(self.action_space.sample())
        print("Example of space.")
        print(self.observation_space.sample())

    def __run_peersim(self):
        self.__run_counter += 1
        self.simulator.run()

    def _get_obs(self):
        # Retrieve Observation
        space_url = self.url_api + self.url_state_path
        headers_state = {"Accept": "application/json", "Connection": "keep-alive"}
        s = requests.get(space_url, headers=headers_state).json()
        obs = {'n_i': s.get("state").get('n_i'),
               'Q': np.asarray(s.get("state").get("Q")),
               'w': np.asarray([s.get("state").get("w")])
               }
        # print(obs)
        done = s.get("done")
        self._observation = obs
        self._done = done
        return obs, done

    def _send_action(self, action):
        payload = {"nodeId": action["target_node"], "noTasks": action["offload_amount"]}
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_action_path
        r = requests.post(action_url, json=payload, headers=headers_action)
        return r

    def __is_up(self):
        payload = {}
        headers_action = {"Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_isUp
        try:
            r = requests.get(action_url, json=payload, headers=headers_action)
            status = r.content == 'True' or r.content == 'true' or r.content
        except requests.exceptions.ConnectionError:
            # if the Server is not yet reachable. We wait.
            status = False
        return status