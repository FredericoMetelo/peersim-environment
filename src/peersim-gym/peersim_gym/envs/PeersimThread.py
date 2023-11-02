# Python program raising
# exceptions in a python
# thread

import threading
import ctypes
import time
import subprocess
import os

def run_peersim(config_path, jar_path="Environment/peersim-srv-0.0.1-SNAPSHOT.jar", output_redirect=None):
    # subprocess.call("pwd", cwd="/home/fm/Documents/Thesis/peersim-srv")
    if output_redirect is None:
        return subprocess.Popen(['java', '-jar', f'{jar_path}', f'{config_path}'], cwd="/")
    else:
        # log = open(output_redirect, 'w')
        return subprocess.Popen(['java', '-jar', f'{jar_path}', f'{config_path}'], cwd="/", stderr=output_redirect, stdout=output_redirect)



class PeersimThread(threading.Thread):
    def __init__(self, name, configs, jar_path=None):
        threading.Thread.__init__(self)
        self.peersim = None
        self.name = name
        self.config_path = configs
        self.this_dir, self.this_filename = os.path.split(__file__)
        if jar_path is None:
            self.jar_path = os.path.join(self.this_dir, "Environment", "peersim-srv-0.0.1-SNAPSHOT.jar")
        else:
            self.jar_path = jar_path
        self.current_outputfile = None
        # self.setDaemon(True)

    def run(self, output_file=None):
        self.current_outputfile = None
        if not (output_file == None):
            self.current_outputfile = open(output_file, 'a')
        self.peersim = run_peersim(self.config_path, jar_path=self.jar_path, output_redirect=self.current_outputfile)


    def get_id(self):
        # returns id of the respective thread
        if hasattr(self, '_thread_id'):
            return self._thread_id
        for id, thread in threading._active.items():
            if thread is self:
                return id

    def stop(self):
        thread_id = self.get_id()
        if self.current_outputfile is not None:
            self.current_outputfile.close()
            self.current_outputfile = None
        self.peersim.kill()  # was terminated
        res = ctypes.pythonapi.PyThreadState_SetAsyncExc(thread_id, ctypes.py_object(SystemExit))
        if res > 1:
            ctypes.pythonapi.PyThreadState_SetAsyncExc(thread_id, 0)
            print('Exception raise failure')