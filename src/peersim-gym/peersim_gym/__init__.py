from gym.envs.registration import register

register(
    id='peersim_gym/PeersimEnv-v0',
    entry_point='peersim_gym.envs:PeersimEnv',
)

