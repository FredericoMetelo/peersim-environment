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
    print("State:\n" + s)


def send_action(action):
    payload = {"nodeId": action["target_node"], "noTasks": action["offload_amount"]}
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
    a = ''
    while a != "quit":
        a = input(">")
        if a == 'quit' or a == 'q':
            break
        elif a == 's':
            get_state()
        else:
            action = env.action_space.sample()
            print(action)
            r = send_action(action)
            print(r)
