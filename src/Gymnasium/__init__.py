import subprocess as s
import logging
import threading
import time

from src.Gymnasium.PeersimThread import PeersimThread

if __name__ == '__main__':
    x = PeersimThread("Run0")
    x.start()
    print("it's nonblocking")
    a = ""
    while(a != "quit"):
        a = input(">")
        print("not blocked and shit")
        if a == "kill":
            x.stop()
