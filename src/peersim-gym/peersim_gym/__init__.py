from gymnasium.envs.registration import register
import json

register(
    id='peersim_gym/PeersimEnv-v0',
    entry_point='peersim_gym.envs:PeersimEnv',
)