from peersim_gym.envs.PeersimEnv import PeersimEnv
import gymnasium as gym
if __name__ == "__main__":
    # env = gym.make("peersim_gym/PeersimEnv-v0")
#    p.generate_config_file()
    # configs_dict = {"protocol.ctrl.r_u": "999", "protocol.props.B": "1"}
    # configs_dict="/home/fm/Documents/Thesis/peersim-srv/configs/examples/default-config.txt"
    configs_dict = None
    env = gym.make("peersim_gym/PeersimEnv-v0")
    env.env.init(configs={
                            "SIZE": "3",
                            "CYCLES": "100",
                            "protocol.clt.taskArrivalRate": "0.4",
                            "protocol.clt.numberOfTasks": "2",
                            "protocol.clt.T": "150,200",
                            "protocol.clt.I":  "200e6,250e6",
                            "protocol.clt.CPI": "1,1",
                            "protocol.clt.weight": "1,1"
                          },
                 log_dir='/home/fm/Documents/Thesis/peersim-srv/logs/')  # TODO mention that you can configure log directory

    a = ""
    s, _ = env.reset()
    while a != "quit":
        a = input(">")
        if a == 'quit' or a == 'q':
            break
        if a == 'r':
            env.reset()
        action = env.action_space.sample()
        print(action)
        state = env.step(action=action)
        print(state)
    env.close()
