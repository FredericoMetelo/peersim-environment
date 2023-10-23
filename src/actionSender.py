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
    print( s)


def send_action(action):
    payload = {"nodeId": str(action["target_node"]), "noTasks": str(action["offload_amount"])}
    headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
    action_url = url_api + url_action_path
    r = requests.post(action_url, json=payload, headers=headers_action)
    return r

if __name__ == "__main__":

    # env = gym.make("peersim_gym/PeersimEnv-v0")
    #    p.generate_config_file()
    # configs_dict = {"protocol.ctrl.r_u": "999", "protocol.props.B": "1"}
    # configs_dict="/home/fm/Documents/Thesis/peersim-srv/configs/examples/default-config.txt"
    configs_dict = None
    env = gym.make("peersim_gym/PeersimEnv-v0")
    env.env.init(configs={
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

                    "protocol.clt.numberOfDAG": "2",
                    "protocol.clt.dagWeights": "1;2",
                    "protocol.clt.edges": "0 1,1 2,1 3,1 6,2 4,3 4,6 5,4 5 ;0 1,1 2,1 3,2 4,3 4",
                    "protocol.clt.maxDeadline": "10",
                    "protocol.clt.vertices": "7;5",

                    "protocol.wrk.NO_CORES": "4",
                    "protocol.wrk.FREQ": "1e7",
                    "protocol.wrk.Q_MAX": "10",
                    "protocol.props.B": "2",
                    "protocol.props.Beta1": "0.001",
                    "protocol.props.Beta2": "4",
                    "protocol.props.P_ti": "20"
                })
    env.reset()

    a = ''
    while a != "quit":
        a = input(">")
        if a == 'quit' or a == 'q':
            break
        elif a == 's':
            get_state()
        else:
            action = {}
            if a.isdigit():
                target = int(a)
                action["target_node"] = target # I am aware of the back and fort between string and int...
                action["offload_amount"] = 1
            else:
                action = env.action_space.sample()
            print(action)
            r = send_action(action)
            print(r)
