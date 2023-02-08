# Python program raising
# exceptions in a python
# thread

import threading
import ctypes
import time
import subprocess


def run_peersim():
    # subprocess.call("pwd", cwd="/home/fm/Documents/Thesis/peersim-srv")
    return subprocess.Popen(
        ['/opt/maven/bin/mvn', 'spring-boot:run', '-Dspring-boot.run.arguments=configs/config-SDN.txt'],
        cwd="/home/fm/Documents/Thesis/peersim-srv")


class PeersimThread(threading.Thread):
    def __init__(self, name):
        threading.Thread.__init__(self)
        self.peersim = None
        self.name = name

    def run(self):
        self.peersim = run_peersim()


    def get_id(self):
        # returns id of the respective thread
        if hasattr(self, '_thread_id'):
            return self._thread_id
        for id, thread in threading._active.items():
            if thread is self:
                return id

    def stop(self):
        thread_id = self.get_id()
        self.peersim.terminate()
        res = ctypes.pythonapi.PyThreadState_SetAsyncExc(thread_id, ctypes.py_object(SystemExit))
        if res > 1:
            ctypes.pythonapi.PyThreadState_SetAsyncExc(thread_id, 0)
            print('Exception raise failure')

