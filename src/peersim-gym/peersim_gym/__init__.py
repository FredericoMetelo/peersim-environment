from gym.envs.registration import register
from peersim_gym.envs.PeersimThread import PeersimThread
import peersim_gym.envs.PeersimConfigGenerator
import pkg_resources

register(
    id='peersim_gym/PeersimEnv-v0',
    entry_point='peersim_gym.envs:PeersimEnv',
)

