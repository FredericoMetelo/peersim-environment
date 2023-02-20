PEERSIM_DEFAULTS = {
    "SIZE" : "10",
    "CYCLE": "1",
    "CYCLES": "1000",
    "MINDELAY": "0",
    "MAXDELAY": "0",
    "DROP": "0",
    "protocol.ctrl.r_u": "1",
    "protocol.ctrl.X_d": "1",
    "protocol.ctrl.X_o": "150",
    "protocol.clt.CPI": "1",
    "protocol.clt.T": "150",
    "protocol.clt.I": "200e6",
    "protocol.wrk.NO_CORES": "4",
    "protocol.wrk.FREQ": "1e7",
    "protocol.wrk.Q_MAX": "10",
    "protocol.props.B": "2",
    "protocol.props.Beta1": "0.001",
    "protocol.props.Beta2": "4",
    "protocol.props.P_ti": "20"
}
BASE_FILE_PATH = "src/peersim-gym/peersim_gym/envs/config-SDN-BASE.txt"
TARGET_FILE_PATH = "configs/config.txt"
def generate_config_file(config_dict):
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
            print(line)
            newFile.write(line)
        # Append the baselines
        for line in fileoutput:
            newFile.write(line)
            print(line)
        baseFile.close()
        newFile.close()
