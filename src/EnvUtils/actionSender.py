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

def _make_ctr(controllers):
    s = ""
    for i in range(len(controllers)):
        s += controllers[i]
        if i < len(controllers) - 1:
            s +=";"
    return s

controllers = ["0", "1"]


if __name__ == "__main__":

    # env = gym.make("peersim_gym/PeersimEnv-v0")
    #    p.generate_config_file()
    # configs_dict = {"protocol.ctrl.r_u": "999", "protocol.props.B": "1"}
    # configs_dict="/home/fm/Documents/Thesis/peersim-srv/configs/examples/default-config.txt"
    env = PeersimEnv(configs={
        "SIZE": "10",
        "CYCLE": "1",
        "CYCLES": "1000",
        "random.seed": "1234567890",
        "MINDELAY": "0",
        "MAXDELAY": "0",
        "DROP": "0",
        "CONTROLLERS": _make_ctr(controllers),

        "protocol.mng.r_u": "1",
        "protocol.mng.X_d": "1",
        "protocol.mng.X_o": "150",
        "protocol.mng.cycle": "5",

        "protocol.clt.numberOfTasks": "1",
        "protocol.clt.weight": "1",
        "protocol.clt.CPI": "1",
        "protocol.clt.T": "150",
        "protocol.clt.I": "200e6",
        "protocol.clt.taskArrivalRate": "0.2",

        "protocol.clt.numberOfDAG": "1",
        "protocol.clt.dagWeights": "1",
        "protocol.clt.edges": "0->1,1->2,2->3,3->4,4->5,5->6,6->7,0->8,8->7,7->9",
        "protocol.clt.maxDeadline": "100",
        "protocol.clt.vertices": "10",

        "protocol.wrk.NO_CORES": "4",
        "protocol.wrk.FREQ": "1e7",
        "protocol.wrk.Q_MAX": "10",

        "protocol.props.B": "2",
        "protocol.props.Beta1": "0.001",
        "protocol.props.Beta2": "4",
        "protocol.props.P_ti": "20",
        "init.Net1.r": "50"
    })
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
            if a.isdigit(): # does not work!!!
                target = a
                actions = {
                    agent: {
                        env.ACTION_HANDLER_ID_FIELD: agent.split("_")[1],
                        env.ACTION_NEIGHBOUR_IDX_FIELD: target
                    } for agent in env.agents
                }
            else:
                actions = {agent: env.action_space(agent).sample() for agent in env.agents}
            print(actions)
            # r = send_action(env, actions)
            # print(r)
            observations, rewards, terminations, truncations, infos = env.step(actions)
            print(rewards)
