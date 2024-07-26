import os
import random

this_dir, this_filename = os.path.split(__file__)
print(this_dir + "    " + this_filename)

PEERSIM_DEFAULTS = {
    "random.seed": "1234567890",
    "SCALE": "1",
    "SIZE": "6",
    "CYCLE": "1",
    "CYCLES": "1000",
    "MINDELAY": "0",
    "MAXDELAY": "0",
    "DROP": "0",
    "CONTROLLERS": "0;5",

    "CLOUD_EXISTS": "1",
    "NO_LAYERS": "2",
    "NO_NODES_PER_LAYERS": "5,1",
    "CLOUD_ACCESS": "0,1",
    "CLOUD_POS": "300,300",
    "clientLayers": "0",

    "FREQS": "1e7,3e7",
    "NO_CORES": "4,8",
    "Q_MAX": "10,50",
    "VARIATIONS": "1e3,1e3",

    "protocol.cld.no_vms": "3",
    "protocol.cld.VMProcessingPower": "1e8",

    "init.Net1.r": "500",

    # "protocol.mng.r_u": "1",
    # "protocol.mng.X_d": "1",
    # "protocol.mng.X_o": "150",
    "protocol.mng.cycle": "5",

    "protocol.clt.numberOfTasks": "1",
    "protocol.clt.weight": "1",
    "protocol.clt.CPI": "1",
    "protocol.clt.T": "150",
    "protocol.clt.I": "4e7",
    "protocol.clt.taskArrivalRate": "0.6",
    "layersThatGetTasks": "0",

    "protocol.clt.numberOfDAG": "1",
    "protocol.clt.dagWeights": "1",
    "protocol.clt.edges": "",
    "protocol.clt.minDeadline": "100",
    "protocol.clt.vertices": "1",

    "protocol.clt.defaultCPUWorkload": "100000000",
    "protocol.clt.defaultMemoryWorkload": "100",
    "protocol.clt.workloadPath": "/home/fm/IdeaProjects/peersim-environment/Datasets/alibaba_trace_cleaned.json",

    "protocol.props.B": "2",
    "protocol.props.Beta1": "0.001",
    "protocol.props.Beta2": "4",
    "protocol.props.P_ti": "20",

    "RANDOMIZEPOSITIONS": "true",
    "init.Net0.POSITIONS": "18.55895350495783,17.02475796027715;47.56499372388999,57.28732691557995;5.366872150976409,43.28729893321355;17.488160666668694,29.422819514162434;81.56549175388358,53.14564532018814;85.15660881172089,74.47408014762478;18.438454887921974,44.310130148722195;72.04311826903107,62.06952644109185;25.60125368295145,15.54795598202745;17.543669122835837,70.7258178169151",

    "RANDOMIZETOPOLOGY": "true",
    "init.Net1.TOPOLOGY": "0,1,2,3,6,8;1,0,2,3,4,5,6,7,8,9;2,0,1,3,6,8,9;3,0,1,2,6,8,9;4,1,5,7;5,1,4,7;6,0,1,2,3,8,9;7,1,4,5;8,0,1,2,3,6;9,1,2,3,6",
    "MANUAL_CONFIG": "false",
    "MANUAL_CORES": "1",
    "MANUAL_FREQS": "1e7",
    "MANUAL_QMAX": "10",
    "clientIsSelf": "1",
    "protocol.urt.channelTypes": "PeersimSimulator.peersim.env.Transport.WirelessSNR",
    "protocol.urt.channelTypesBetweenLayers": "0,0,0,0,-1,-1,0,-1,1,-1;0,0,0,0,0,0,0,0,1,1;0,0,0,0,-1,-1,0,-1,1,1;0,0,0,0,-1,-1,0,-1,1,1;-1,0,-1,-1,0,0,-1,0,-1,-1;-1,0,-1,-1,0,0,-1,0,-1,-1;0,0,0,0,-1,-1,0,-1,1,1;-1,0,-1,-1,0,0,-1,0,-1,-1;0,0,0,0,-1,-1,0,-1,1,-1;-1,0,0,0,-1,-1,0,-1,-1,1",
    "protocol.urt.SNR": "45",

    "protocol.wrk.energyCostComm": "10",
    "protocol.wrk.energyCostComp": "1",
}

rescalable_parameters = ["MINDELAY", "MAXDELAY", "FREQS", "protocol.cld.VMProcessingPower"]  # CYCLE, CYCLES, are rescaled in the file itself.


def rescaled_value(key, value, scale):
    if key == "MINDELAY" or key == "MAXDELAY" or key == "protocol.cld.VMProcessingPower": # Just check if value has ";", if not then check if has ",", if not then it is a scalar...
        return str(int(value) / scale)
    elif key == "FREQS":
        freqs = value.split(",")
        rescaled_freqs = [str(int(float(freq) / scale)) for freq in freqs]
        rescaled_f_string = ",".join(rescaled_freqs)
        return rescaled_f_string


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
    if "SCALE" in config_dict:
        scale = int(config_dict["SCALE"])
    else:
        scale = int(PEERSIM_DEFAULTS["SCALE"])  # Which is 1...
    with open(TARGET_FILE_PATH, "w") as newFile:
        # Append the configs in the dict
        for key in PEERSIM_DEFAULTS.keys():
            value = config_dict.get(key) if key in config_dict else PEERSIM_DEFAULTS.get(key)
            if key in rescalable_parameters:
                value = rescaled_value(key, value, scale)
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

def randomize_seed(file_path):
    """
    Codepilot generated method.
    This function reads the first line of a file, and if the line starts with random.seed then it changes the preceeding
    value to a random number of the same magnitude. For example, if the first line is "random.seed 1234567890", then
    when the function is called it would become "random.seed 8234466895"

    :param file_path:
    :return:
    """
    with open(file_path, 'r') as file:
        first_line = file.readline().strip()

    # Check if the first line starts with "random.seed"
    if first_line.startswith("random.seed"):
        # Extract the current seed value
        current_seed = int(first_line.split()[-1])

        # Generate a new random number with the same magnitude
        magnitude = 10 ** len(str(current_seed))
        new_seed = random.randint(magnitude, 10 * magnitude - 1)

        # Create the updated line with the new seed
        updated_line = f"random.seed {new_seed}"

            # Replace the first line in the file
        with open(file_path, 'r') as file:
            content = file.read()
        with open(file_path, 'w') as file:
            file.write(content.replace(first_line, updated_line))

        return new_seed