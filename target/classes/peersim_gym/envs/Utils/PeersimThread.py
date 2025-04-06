# Python program raising
# exceptions in a python
# thread
import multiprocessing
import threading
import ctypes
import time
import subprocess
import os


def run_peersim(config_path, port, jar_path="Environment/peersim-srv-0.0.1-SNAPSHOT.jar", output_redirect=None):
    # subprocess.call("pwd", cwd="/home/fm/Documents/Thesis/peersim-srv")
    # os.system("java --version")
    if output_redirect is None:
        return subprocess.Popen(['java', '-jar', f'{jar_path}', f'{config_path}', f'--server.port={port}'], cwd="/", stderr=subprocess.DEVNULL,
                                stdout=subprocess.DEVNULL)
    if output_redirect == "stdout":
        return subprocess.Popen(['java', '-jar', f'{jar_path}', f'{config_path}' f'--server.port={port}'], cwd="/")
    else:
        # log = open(output_redirect, 'w')
        return subprocess.Popen(['java', '-jar', f'{jar_path}', f'{config_path}', f'--server.port={port}'], cwd="/", stderr=output_redirect,
                                stdout=output_redirect)

# class PeersimProcess(multiprocessing.Process):
#     def __init__(self, config_path, port, jar_path, output_file=None):
#         super().__init__()
#         self.config_path = config_path
#         self.port = port
#         self.jar_path = jar_path
#         self.output_file = output_file
#         self.process_handle = None
#
#     def run(self):
#         out_handle = None
#         if self.output_file is not None:
#             out_handle = open(self.output_file, 'a+')
#         self.process_handle = run_peersim(
#             self.config_path, self.port, jar_path=self.jar_path,
#             output_redirect=out_handle
#         )
#
# class PeersimThread:
#     def __init__(self, name, configs, jar_path=None):
#         self.name = name
#         self.config_path = configs
#         self.this_dir, self.this_filename = os.path.split(__file__)
#         if jar_path is None:
#             self.jar_path = os.path.join(self.this_dir, "../Environment", "peersim-srv-0.0.1-SNAPSHOT.jar")
#         else:
#             self.jar_path = jar_path
#         self.current_outputfile = None
#
#         self.process = None
#
#
#     def run(self, port, output_file=None):
#         self.stop()
#         self.process = PeersimProcess(self.config_path, port, self.jar_path, output_file)
#         self.process.start()
#
#     def get_id(self):
#         return self.process.pid
#
#     def stop(self):
#         if self.process and self.process.is_alive():
#             if self.process.process_handle:
#                 self.process.process_handle.kill()
#             self.process.terminate()
#             self.process.join()
#         self.process = None

class PeersimThread(threading.Thread):
    def __init__(self, name, configs, jar_path=None):
        threading.Thread.__init__(self)
        self.peersim = None
        self.name = name
        self.config_path = configs
        self.this_dir, self.this_filename = os.path.split(__file__)
        if jar_path is None:
            self.jar_path = os.path.join(self.this_dir, "../Environment", "peersim-srv-0.0.1-SNAPSHOT.jar")
        else:
            self.jar_path = jar_path
        self.current_outputfile = None
        # self.setDaemon(True)

    def run(self, port, output_file=None):
        self.current_outputfile = None
        if not (output_file == None):
            self.current_outputfile = open(output_file, 'a+')

        self.peersim = run_peersim(self.config_path, port, jar_path=self.jar_path, output_redirect=self.current_outputfile)

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
