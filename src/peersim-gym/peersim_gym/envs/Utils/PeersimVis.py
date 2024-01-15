import pygame

# State Fields
GLOBAL_STATE = "globalState"
GLOBAL_STATE_POSITIONS = "positions"
GLOBAL_STATE_NODE_IDS = "nodeIds"
GLOBAL_STATE_Q = "Q"
GLOBAL_STATE_LAYERS = "layers"
COORD_X = "X"
COORD_Y = "Y"

# Constants
NODE_W = 15
NODE_H = 15

FONT_SIZE = 15

INFO_W = 100
INFO_H = 40

NODE_COLOR = (133, 196, 120)  # GREEN
NODE_OVERLOADED_COLOR = (255, 0, 0)  # RED
NODE_TEXT_COLOR = (255, 255, 255)  # WHITE
NODE_INFO_BACKGROUND_COLOR = (251, 234, 205)  # CREAM
NODE_INFO_TEXT_COLOR = (0, 0, 0)  # BLACK

LINK_PASSED_TASK_COLOR = (255, 0, 0)  # RED
LINK_NO_ACTION_COLOR = (0, 0, 0)  # BLACK
LOCAL_PROCESSING_BACKGROUND_COLOR = (0, 0, 255)  # BLUE
LOCAL_PROCESSING_TEXT_COLOR = (255, 255, 255)  # WHITE

pygame.init()
displayw = 1000 + INFO_W
displayh = 1000 + INFO_H
windowclock = pygame.time.Clock()


class PeersimVis(object):
    def __init__(self, has_cloud):
        # Display dimensions
        self.displayw = displayw
        self.displayh = displayh
        self.has_cloud = has_cloud

        # Game font
        self.font = pygame.font.SysFont("monospace", FONT_SIZE)

        # Initialize the display
        self.display = pygame.display.set_mode((self.displayw, self.displayh))
        pygame.display.set_caption("Peersim Simulaiton Visualization")
        self.display.fill((255, 255, 255))
        self.display.blit(self.display, (0, 0))

    def draw(self):
        pygame.display.update()
        windowclock.tick(60)

    def update_state(self, state, max_Q, neighborsMatrix, last_actions, controllers, agent_name_mapping):
        # There is a better way of doing this without passing every parameter in existence for sure... But, I'm tired af
        """
        Update the state of the visualization using the state provided by the simulator.
        :return:
        """
        global_state = state[GLOBAL_STATE] if GLOBAL_STATE in state else state
        global_positions = global_state[GLOBAL_STATE_POSITIONS]
        global_ids = global_state[GLOBAL_STATE_NODE_IDS]
        global_Q = global_state[GLOBAL_STATE_Q]
        global_layers = global_state[GLOBAL_STATE_LAYERS]

        # node x sent action to node y
        actions = { node_id: last_actions[agent_name]['neighbourIndex'] for agent_name, node_id in agent_name_mapping.items() }

        # Clear the display
        self.display.fill((255, 255, 255))

        self.draw_links(neighborsMatrix, global_positions, actions)
        self.draw_nodes(global_Q, global_ids, global_layers, global_positions, max_Q)

    def draw_nodes(self, global_Q, global_ids, global_layers, global_positions, max_Q):
        for i in range(len(global_ids)):
            x = self.project_x(i, global_positions)  # Project the coordinates to the display 100x100 to 1000x1000
            y = self.project_y(i, global_positions)
            layer = global_layers[i]
            Q = global_Q[i]
            id = global_ids[i]
            overloaded = Q >= max_Q[layer]
            # To identify if an action passed through this link, we check if the last action of the node was to the
            # target node



            self.draw_node(x, y, layer, Q, id, overloaded)

    def draw_node(self, x, y, layer, Q_size, id, overloaded=False):
        color = NODE_COLOR if not overloaded else NODE_OVERLOADED_COLOR
        info_x = x + NODE_W / 2
        info_y = y + NODE_W / 2

        info_rect = pygame.draw.rect(self.display, NODE_INFO_BACKGROUND_COLOR, (info_x, info_y, INFO_W, INFO_H))
        node_rect = pygame.draw.rect(self.display, color, (x, y, NODE_W, NODE_H))

        # Render and blit id onto node_rect
        id_text_surface = self.font.render(f"{id}", True, NODE_TEXT_COLOR)
        id_rect = id_text_surface.get_rect(center=node_rect.center)  # This returns a rectangle covering the entire word
        self.display.blit(id_text_surface, id_rect)

        Q_surface = self.font.render(f"Q={Q_size}   L={layer}", 1, NODE_INFO_TEXT_COLOR)

        info_rect_height = info_rect.height
        self.display.blit(Q_surface, (info_x + 10, info_y + 10))

    def draw_links(self, link_matrix, positions, actions):
        """
        Draw the links between the nodes.
        :param link_matrix: The link matrix.
        :param positions: The positions of the nodes.
        :return:
        """
        do_not_overrride = {}
        for i in range(len(link_matrix) - self.has_cloud):
            for j in range(len(link_matrix[i])):
                source = i
                target = link_matrix[i][j]
                key = f"{source}-{target}"
                rev_key = f"{target}-{source}"
                if target == len(positions) and self.has_cloud >= 1:
                    continue
                # Problem, the lines are being drawn on top of each other, therefore erasing one another.
                # Another potential problem for later is that there isn't really any distinction between the direction
                # of the task being offloaded.

                is_target = False
                if source in actions:
                    is_target = actions[source] == target

                if key in do_not_overrride or rev_key in do_not_overrride:
                    continue

                x1 = self.project_x(source, positions) + NODE_W / 2
                y1 = self.project_y(source, positions) + NODE_H / 2
                x2 = self.project_x(target, positions) + NODE_W / 2
                y2 = self.project_y(target, positions) + NODE_H / 2
                self.draw_line(x1, y1, x2, y2, is_target)

                if is_target: # Guarantee that drawn lines are not overriden
                    do_not_overrride[key] = target
                    do_not_overrride[rev_key] = target

                if source == target and is_target:
                    local_rect_x = self.project_x(source, positions) + NODE_W
                    local_rect_y = self.project_y(source, positions)
                    local_rect = pygame.draw.rect(self.display, LOCAL_PROCESSING_BACKGROUND_COLOR,
                                                  (local_rect_x, local_rect_y, NODE_W, NODE_H))
                    local_text_surface = self.font.render("L", True, LOCAL_PROCESSING_TEXT_COLOR)
                    local_text_rect = local_text_surface.get_rect(center=local_rect.center)
                    self.display.blit(local_text_surface, local_text_rect)

    def project_y(self, node_idx, positions):
        return positions[node_idx][COORD_Y] * 10

    def project_x(self, node_idx, positions):
        return positions[node_idx][COORD_X] * 10

    def draw_line(self, x1, y1, x2, y2, is_target):
        color = LINK_PASSED_TASK_COLOR if is_target else LINK_NO_ACTION_COLOR
        pygame.draw.line(self.display, color, (x1, y1), (x2, y2), 1)
