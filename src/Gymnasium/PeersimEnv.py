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

from src.Gymnasium.PeersimThread import PeersimThread


class PeersimEnv(gym.Env):
    def __init__(self, render_mode=None, number_nodes=10, max_Q_size=10,
                 max_w=1):  # TODO define behaviour of node on overload
        # ==== Variables to configure the PeerSim
        self.number_nodes = number_nodes
        self.max_Q_size = max_Q_size
        self.max_w = max_w

        self.url_api = "http://localhost:8080"
        self.url_action_path = "/action"
        self.url_state_path = "/state"

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
                "node": Discrete(number_nodes, start=1),
                "Q": MultiDiscrete(q_list),
                "w": Box(high=max_w, low=0)
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
                "target_node": Discrete(number_nodes, start=1),
                "offload_amount": Discrete(max_Q_size, start=1)
            }
        )

        # mvn spring-boot:run -Dspring-boot.run.arguments=configs/config-SDN.txt
        # Run the actual environment.
        # with subprocess as s:
        self.simulator = PeersimThread(name='Run0')
        self.simulator.start()

    def step(self, action: ActType):
        # A step will advance the simulation in 100 ticks
        # Send action.
        payload = {"nodeId": action["target_node"], "noTasks": action["offload_amount"]}
        headers_action = {"content-type": "application/json",  "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_action_path
        r = requests.post(action_url, json=payload, headers=headers_action)
        reward_for_action = float(r.content)
        print(reward_for_action)
        # Retrieve Observation
        space_url = self.url_api + self.url_state_path
        headers_state = {"Accept": "application/json", "Connection": "keep-alive"}
        s = requests.get(space_url, headers=headers_state)
        print(s.content)
        return s.content, reward_for_action, False, False, None

    def render(self):
        pass

    def __run_peersim(self):
        self.__run_counter += 1
        self.simulator.run()

    def reset(self, **kwargs):
        self.simulator.stop()
        self.__run_peersim()
        pass

    def see_types(self):
        # TODO delete this.
        print("Example of action.")
        print(self.action_space.sample())
        print("Example of space.")
        print(self.observation_space.sample())
