import os
this_dir, this_filename = os.path.split(__file__)
print(this_dir +  "    " + this_filename)

PEERSIM_DEFAULTS = {
    "SIZE" : "10",
    "CYCLE": "1",
    "CYCLES": "1000",
    "random.seed": "1234567890",
    "MINDELAY": "0",
    "MAXDELAY": "0",
    "DROP": "0",
    "protocol.ctrl.r_u": "1",
    "protocol.ctrl.X_d": "1",
    "protocol.ctrl.X_o": "150",
    "protocol.clt.numberOfTasks": "1",
    "protocol.clt.weight": "1",
    "protocol.clt.CPI": "1",
    "protocol.clt.T": "150",
    "protocol.clt.I": "200e6",
    "protocol.clt.taskArrivalRate": "0.2",

    "protocol.clt.numberOfDAG": "1",
    "protocol.clt.dagWeights": "1",
    "protocol.clt.edges": "",
    "protocol.clt.maxDeadline": "10",
    
    "protocol.clt.vertices": "1",
    "protocol.wrk.NO_CORES": "4",
    "protocol.wrk.FREQ": "1e7",
    "protocol.wrk.Q_MAX": "10",
    "protocol.props.B": "2",
    "protocol.props.Beta1": "0.001",
    "protocol.props.Beta2": "4",
    "protocol.props.P_ti": "20"
}
BASE_FILE_PATH = os.path.join(this_dir, "configs", "config-SDN-BASE.txt")
TARGET_FILE_PATH = os.path.join(this_dir, "configs", "config.txt")

def generate_config_file(config_dict, explicit_lines=False):
    print("GENERATING CONFIG FILE")
    if config_dict is None:
        config_dict = {}
    if type(config_dict) is not dict:
        raise Exception("Please pass a dictionary of configurations")

    with open(BASE_FILE_PATH, "r") as baseFile:
        fileoutput = baseFile.readlines()

    with open(TARGET_FILE_PATH, "w") as newFile:
        # Append the configs in the dict
        for key in PEERSIM_DEFAULTS.keys():
            value = config_dict.get(key) if key in config_dict else PEERSIM_DEFAULTS.get(key)
            line = key + " " + value + "\n"
            if explicit_lines:
                print(line)
            newFile.write(line)
        # Append the baselines
        for line in fileoutput:
            newFile.write(line)
            if explicit_lines:
                print(line)
        baseFile.close()
        newFile.close()
