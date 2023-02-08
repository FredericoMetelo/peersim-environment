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

from src.Gymnasium import PeersimThread


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
        self.x = PeersimThread(f'Run{self.__run_counter}')
        self.x.start()

    def step(self, action: ActType):
        # A step will advance the simulation in 100 ticks
        # Send action.
        requests.post()

        # Retrieve Observation
        requests.get()

        # Retrieve Reward
        requests.get()

        return

    def render(self):
        pass

    def __run_peersim(self):
        self.__run_counter += 1
        self.x.run()

    def reset(self, **kwargs):
        self.x.stop()
        self.__run_peersim()
        pass


