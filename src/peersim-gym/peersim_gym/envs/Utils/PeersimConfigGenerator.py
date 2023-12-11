import os

this_dir, this_filename = os.path.split(__file__)
print(this_dir + "    " + this_filename)
PEERSIM_DEFAULTS = {
    "SIZE": "6",
    "CYCLE": "1",
    "CYCLES": "1000",
    "random.seed": "1234567890",
    "MINDELAY": "0",
    "MAXDELAY": "0",
    "DROP": "0",
    "CONTROLLERS": "0;5",

    "CLOUD_EXISTS": "1",
    "NO_LAYERS": "2",
    "NO_NODES_PER_LAYERS": "5,1",
    "CLOUD_ACCESS": "0,1",

    "FREQS": "1e7,3e7",
    "NO_CORES": "4,8",
    "Q_MAX": "10,50",
    "VARIATIONS": "1e3,1e3",

    "protocol.cld.no_vms": "3",
    "protocol.cld.VMProcessingPower": "1e8",

    "init.Net1.r": "500",

    "protocol.mng.r_u": "1",
    "protocol.mng.X_d": "1",
    "protocol.mng.X_o": "150",
    "protocol.mng.cycle": "5",

    "protocol.clt.numberOfTasks": "1",
    "protocol.clt.weight": "1",
    "protocol.clt.CPI": "1",
    "protocol.clt.T": "150",
    "protocol.clt.I": "4e7",
    "protocol.clt.taskArrivalRate": "0.6",

    "protocol.clt.numberOfDAG": "1",
    "protocol.clt.dagWeights": "1",
    "protocol.clt.edges": "",
    "protocol.clt.maxDeadline": "100",
    "protocol.clt.vertices": "1",

    "protocol.props.B": "2",
    "protocol.props.Beta1": "0.001",
    "protocol.props.Beta2": "4",
    "protocol.props.P_ti": "20",
}


def generate_config_file(config_dict, simtype, explicit_lines=False):
    BASE_FILE_PATH = os.path.join(this_dir, "../configs", f"config-{simtype}-BASE.txt")
    TARGET_FILE_PATH = os.path.join(this_dir, "../configs", "config.txt")

    print("GENERATING CONFIG FILE")
    controllers = []
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
            if key == "CONTROLLERS":
                controllers = [int(s) for s in value.strip().split(";")]
            newFile.write(line)
        # Append the baselines
        for line in fileoutput:
            newFile.write(line)
            if explicit_lines:
                print(line)
        baseFile.close()
        newFile.close()
        return controllers, TARGET_FILE_PATH


def compile_dict(file_path):
    try:
        with open(file_path, 'r') as file:
            _dict = {}
            for line in file:
                if 'CONTROLLERS' in line:
                    # Find the position of "CONTROLLERS" in the line
                    # Extract everything after "CONTROLLERS"
                    result = line[len('CONTROLLERS'):].strip()
                    controllers = [int(s) for s in result.strip().split(";")]
                else:
                    result = line.split(" ")
                    if result[0] in PEERSIM_DEFAULTS:
                        _dict[result[0]] = line[len(result[0]):].strip()
            return controllers, _dict
    except FileNotFoundError:
        return "File not found"
