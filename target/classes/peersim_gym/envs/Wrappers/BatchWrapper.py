from gymnasium.spaces import Dict, Discrete, Sequence
import requests
from envs import PeersimEnv
from envs.PeersimEnv import AGENT_PREFIX


def parse_list_of_ints(str_list_of_ints):
    substring_list = str_list_of_ints[1:-1].split(',')
    actual_list = [int(substring) for substring in substring_list]
    return actual_list


class BatchEnvWrapper(PeersimEnv):
    def __init__(self, env: PeersimEnv):
        super().__init__(env, simtype="Batch")
        assert isinstance(
            env, PeersimEnv
        ), ("BatchEnvWrapper is only compatible with AEC environments. Please convert the environment to AEC using "
            "pettingzoo.utils.parallel_to_aec(), you can convert it back with pettingzoo.utils.aec_to_parallel()")
        self._env = env
        self._action_spaces = {
            agent: Dict(
                {
                    self.ACTION_HANDLER_ID_FIELD: Sequence(Discrete(self.number_nodes - 1, start=0)),
                    self.ACTION_NEIGHBOUR_IDX_FIELD: Discrete(self.number_nodes - 1, start=0)
                }
            ) for agent in self.possible_agents
        }

    def __send_action(self, action):
        payload = [
            {
                self.ACTION_TYPE_FIELD: self.simtype,
                self.ACTION_NEIGHBOUR_IDX_FIELD: parse_list_of_ints(action.get(name).get(self.ACTION_NEIGHBOUR_IDX_FIELD)),
                self.ACTION_HANDLER_ID_FIELD: int(self.agent_name_mapping.get(name))
            } for name in self.agent_name_mapping.keys()
        ]
        headers_action = {"content-type": "application/json", "Accept": "application/json", "Connection": "keep-alive"}
        action_url = self.url_api + self.url_action_path
        try:
            r = requests.post(action_url, json=payload, headers=headers_action, timeout=self.default_timeout).json()
            result = {AGENT_PREFIX + str(reward["srcId"]): reward for reward in r}
            self._result = result
            return result
        except requests.exceptions.Timeout:
            print("Failed to send action, could not connect to the environment. Returning old result.")
            return self._result

    def action_space(self, agent):
        return Dict(
            {
                self.ACTION_HANDLER_ID_FIELD: Sequence(Discrete(self.number_nodes - 1, start=1)),
                self.ACTION_NEIGHBOUR_IDX_FIELD: Discrete(self.max_Q_size, start=1)
            }
        )
