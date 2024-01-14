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
NODE_W = 10
NODE_H = 10

FONT_SIZE = 10

INFO_W = 80
INFO_H = 40

NODE_COLOR = (133, 196, 120)  # GREEN
NODE_OVERLOADED_COLOR = (255, 0, 0)  # RED
NODE_TEXT_COLOR = (255, 255, 255)  # WHITE
NODE_INFO_BACKGROUND_COLOR = (251, 234, 205)  # CREAM
NODE_INFO_TEXT_COLOR = (0, 0, 0)  # BLACK

pygame.init()
displayw = 1000 + INFO_W
displayh = 1000 + INFO_H
windowclock = pygame.time.Clock()


class PeersimVis(object):
    def __init__(self):
        # Display dimensions
        self.displayw = displayw
        self.displayh = displayh

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

    def update_state(self, state, max_Q):
        """
        Update the state of the visualization using the state provided by the simulator.
        :return:
        """
        global_state = state[GLOBAL_STATE] if GLOBAL_STATE in state else state
        global_positions = global_state[GLOBAL_STATE_POSITIONS]
        global_ids = global_state[GLOBAL_STATE_NODE_IDS]
        global_Q = global_state[GLOBAL_STATE_Q]
        global_layers = global_state[GLOBAL_STATE_LAYERS]

        # Clear the display
        self.display.fill((255, 255, 255))

        # Draw the nodes
        for i in range(len(global_ids)):
            x = global_positions[i][COORD_X] * 10  # Project the coordinates to the display 100x100 to 1000x1000
            y = global_positions[i][COORD_Y] * 10
            layer = global_layers[i]
            Q = global_Q[i]
            id = global_ids[i]
            overloaded = Q >= max_Q[layer]
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


