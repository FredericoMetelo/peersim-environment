import random

from peersim_gym.envs.PeersimEnv import PeersimEnv
import gymnasium as gym

import requests

url_api = "http://localhost:8080"
url_action_path = "/action"
url_state_path = "/state"
url_isUp = "/up"


def get_state():
    space_url = url_api + url_state_path
    headers_state = {"Accept": "application/json", "Connection": "keep-alive"}
    s = requests.get(space_url, headers=headers_state).json()
    print(s)


def send_action(env, action):
    payload = {
        env.agents[i]: {
            "neighbourIndex": str(action[i]),
            "controllerId": str(action[i])
        } for i in range(len(env.agents))
    }
    headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
    action_url = url_api + url_action_path
    r = requests.post(action_url, json=payload, headers=headers_action)
    return r


def _make_ctr(ctrs_list):
    s = ""
    for i in range(len(ctrs_list)):
        s += ctrs_list[i]
        if i < len(ctrs_list) - 1:
            s += ";"
    return s


controllers = ["0", "5"]

if __name__ == "__main__":

    # env = gym.make("peersim_gym/PeersimEnv-v0")
    #    p.generate_config_file()
    # configs_dict = {"protocol.ctrl.r_u": "999", "protocol.props.B": "1"}
    # configs_dict="/home/fm/Documents/Thesis/peersim-srv/configs/examples/default-config.txt"
    configs = {
        "SIZE": "6",
        "CYCLE": "1",
        "CYCLES": "1000",
        "random.seed": "1234567890",
        "MINDELAY": "0",
        "MAXDELAY": "0",
        "DROP": "0",
        "CONTROLLERS": _make_ctr(controllers),

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

    env = PeersimEnv(configs=configs, simtype="basic")
    # Legacy Configs:
    #         "protocol.wrk.NO_CORES": "4",
    #         "protocol.wrk.FREQ": "1e7",
    #         "protocol.wrk.Q_MAX": "10",

    env.reset()

    a = ''
    while a != "quit":
        a = input(">")
        if a == 'quit' or a == 'q':
            env.close()
            break
        elif a == 's':
            get_state()
        else:
            actions = {}
            if a.isdigit():  # does not work!!!
                target = str(a)
                actions = {
                    agent: {
                        env.ACTION_HANDLER_ID_FIELD: agent.split("_")[1],
                        env.ACTION_NEIGHBOUR_IDX_FIELD: target
                    } for agent in env.agents
                }
            else:
                for agent in env.agents:
                    actions = {
                        agent: {
                            env.ACTION_HANDLER_ID_FIELD: agent.split("_")[1],
                            env.ACTION_NEIGHBOUR_IDX_FIELD: random.Random().randint(0, int(configs["SIZE"]))
                        } for agent in env.agents
                    }
            print(actions)
            # r = send_action(env, actions)
            # print(r)
            observations, rewards, terminations, truncations, infos = env.step(actions)
            print(rewards)
