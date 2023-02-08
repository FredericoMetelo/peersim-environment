import subprocess as s
import logging
import threading
import time

from src.Gymnasium.PeersimEnv import PeersimEnv
from src.Gymnasium.PeersimThread import PeersimThread

if __name__ == '__main__':
    env = PeersimEnv("Run0")
    env.see_types()
    print("it's nonblocking")
    a = ""
    while(a != "quit"):
        a = input(">")
        action = {
            "target_node": 8,
            "offload_amount": 1
        }
        env.step(action=action)
        print("await input, type kill to kill console.")
        if a == "kill":
            break
