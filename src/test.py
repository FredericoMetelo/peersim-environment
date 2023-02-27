from peersim_gym.envs.PeersimEnv import PeersimEnv
import gym
if __name__ == "__main__":
    # env = gym.make("peersim_gym/PeersimEnv-v0")
#    p.generate_config_file()
    # configs_dict = {"protocol.ctrl.r_u": "999", "protocol.props.B": "1"}
    # configs_dict="/home/fm/Documents/Thesis/peersim-srv/configs/examples/default-config.txt"
    configs_dict = None
    env = gym.make("peersim_gym/PeersimEnv-v0")
    a = ""
    env.reset()
    while a != "quit":
        a = input(">")
        action = env.action_space.sample()
        print(action)
        state = env.step(action=action)
        print(state)
        env.reset()
    env.close()
