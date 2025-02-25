import copy
import json
import math
from random import random

from matplotlib import pyplot as plt

from EtherTopologyReader import coordinates_key


class CoordinateGenerator:
    def __init__(self, x_range, y_range):
        self.x_range = x_range
        self.y_range = y_range

    def generate(self):
        return (random() * self.x_range, random() * self.y_range)

class RandomCoordinateGenerator(CoordinateGenerator):
    def __init__(self, x_range, y_range):
        super().__init__(x_range, y_range)

    def generate(self):
        return super().generate()

class GridCoordinateGenerator(CoordinateGenerator):
    def __init__(self, x_range, y_range, x_spacing, y_spacing):
        super().__init__(x_range, y_range)
        self.x_spacing = x_spacing
        self.y_spacing = y_spacing
        self.last_position = (-self.x_spacing, 0)

    def generate(self):
        x = self.last_position[0] + self.x_spacing
        y = self.last_position[1]
        if x >= self.x_range:
            x = 0
            y += self.y_spacing

        self.last_position = (x, y)
        return (x, y)

class RingCoordinateGenerator(CoordinateGenerator):
    def __init__(self, x_range, y_range, radius, angle_update):
        super().__init__(x_range, y_range)
        self.radius = radius
        self.angle = 0
        self.angle_update = angle_update

    def generate(self):
        angle = self.angle
        x = self.x_range / 2 + self.radius * math.cos(angle)
        y = self.y_range / 2 + self.radius * math.sin(angle)
        self.angle = angle + self.angle_update
        return (x, y)
class BiforcatedCoordinateGenerator(CoordinateGenerator):
    def __init__(self, x_range, y_range, no_separation):
        super().__init__(x_range, y_range)
        self.no_separation = no_separation
        self.total_placed = 0

    def generate(self):
        if self.total_placed < self.no_separation:
           # Randomly place the nodes in the first half of the grid_FedAvg
            x = random() * self.x_range
            y = random() * self.y_range
        else:
            x = random() *  self.x_range + self.x_range
            y = random() * self.y_range
        self.total_placed += 1
        return (x, y)

class Capacity:
    def __init__(self, cpu_milis, memory):
        self.cpu_milis = cpu_milis
        self.memory = memory

    def __repr__(self):
        return f"Capacity(cpu_milis={self.cpu_milis}, memory={self.memory})"


class Node:
    def __init__(self, type, arch, capacity, labels, coordinates=None, id=None, name=None):
        self.id = id
        self.arch = arch
        self.name = name
        self.coordinates = coordinates
        self.capacity = capacity
        self.labels = labels
        self.type = type

    def set_id(self, type, id):
        self.name = f"{type}_{id}"
        self.id =  int(random() * (10**15))

    def __repr__(self):
        return (
            f"Device(id={self.id}, arch='{self.arch}', name='{self.name}', "
            f"coordinates={self.coordinates}, capacity={self.capacity}, labels={self.labels})"
        )


class Link:
    def __init__(self, id, source, target, path_bandwidths, min_bandwidth, expected_rtt):
        self.id = id
        self.source = source
        self.target = target
        self.path_bandwidths = path_bandwidths
        self.min_bandwidth = min_bandwidth
        self.expected_rtt = expected_rtt

    def __repr__(self):
        return (
            f"Connection(id='{self.id}', source={self.source}, target={self.target}, "
            f"path_bandwidths={self.path_bandwidths}, min_bandwidth={self.min_bandwidth}, "
            f"expected_rtt={self.expected_rtt})"
        )

class Network:
    def __init__(self):
        self.no_nodes = 0
        self.nodes = {}
        self.links = {}
        self.neighbours = {}

    def sample_next_coords(self, coordinate_scheme):
        if isinstance(coordinate_scheme, CoordinateGenerator):
            return coordinate_scheme.generate()

        if isinstance(coordinate_scheme, str):
            if coordinate_scheme == "random":
                return (random() * 100, random() * 100)
        return (0, 0)


    def add_node(self, node):
        self.nodes[node.id] = node

    def add_link(self, link):
        self.links[link.id] = link
        if link.source not in self.neighbours:
            self.neighbours[link.source] = []
        self.neighbours[link.source].append(link.target)
        if link.target not in self.neighbours:
            self.neighbours[link.target] = []
        self.neighbours[link.target].append(link.source)

    def generate_random_node_permutation_for_generation(self, input_dict):
        # Convert the dictionary to a mutable list of tuples (key, count)
        items = [(key, count) for key, count in input_dict.items()]
        total = sum(count for _, count in items)
        result = []

        while total > 0:
            # Compute probabilities dynamically
            probabilities = [count / total for _, count in items]
            # Sample based on probabilities
            cumulative_prob = []
            current_total = 0
            for _, count in items:
                current_total += count
                cumulative_prob.append(current_total / total)

            # Sample a random number between 0 and 1
            r = random()
            # Find the corresponding index based on cumulative probabilities
            chosen_index = next(i for i, p in enumerate(cumulative_prob) if r <= p)

            chosen_type, _ = items[chosen_index]
            result.append(chosen_type)
            # Update the count for the chosen type
            items[chosen_index] = (chosen_type, items[chosen_index][1] - 1)
            # Remove the type if its count is zero
            if items[chosen_index][1] == 0:
                items.pop(chosen_index)
            # Recompute the total
            total = sum(count for _, count in items)

        return result

    def generate_nodes(self, node_types: list[Node], no_nodes_per_type, coordinate_scheme="random", permutate=True):
        if permutate:
            # pair the node_types with prototype nodes in a dict
            node_types_proto = {node_type.type: node_type for node_type in node_types}
            node_type_gen = self.generate_random_node_permutation_for_generation(no_nodes_per_type)
            counter_per_type = {node_type: 0 for node_type in node_types_proto}
            for node_type in node_type_gen:
                i = counter_per_type[node_type]
                node = copy.deepcopy(node_types_proto[node_type])
                node.set_id(node_type, i)
                node.coordinates = self.sample_next_coords(coordinate_scheme)
                self.add_node(node)
                counter_per_type[node_type] += 1
        else:
            for no_type, node_prototype in zip(no_nodes_per_type, node_types):
                for i in range(no_nodes_per_type[no_type]):
                    node = copy.deepcopy(node_prototype)
                    node.set_id(no_type, i)
                    node.coordinates = self.sample_next_coords(coordinate_scheme)
                    self.add_node(node)


    def generate_fully_connected_topology(self, args_dict):
        # Check if correct arguments are provided
        # For the fully connected topology we need to link all the nodes among each other. We consider a uniform bandwidth
        # for all the links. If args not available throw an error.
        angle_update = 2 * math.pi /self.no_nodes
        coord_scheme = RingCoordinateGenerator(100, 100, 100, angle_update)
        self.generate_nodes(args_dict["node_types"], args_dict["no_nodes_per_type"], coord_scheme)
        if "bandwidth" not in args_dict:
            raise ValueError("Bandwidth not provided for the fully connected topology")
        for src_id in self.nodes:
            for target_id in self.nodes:
                if src_id == target_id:
                    continue
                link = Link(
                    id=f"{src_id}->{target_id}",
                    source=src_id,
                    target=target_id,
                    path_bandwidths=[args_dict["bandwidth"]],
                    min_bandwidth=args_dict["bandwidth"],
                    expected_rtt=0
                )
                self.add_link(link)

    def generate_random_topology(self, args_dict):
        # the random topology needs the argument radius in the args_dict. All nodes are randomly placed.
        # The links are created between nodes that are within the radius.
        angle_update = 2 * math.pi / self.no_nodes
        coord_scheme = RandomCoordinateGenerator(100, 100)
        self.generate_nodes(args_dict["node_types"], args_dict["no_nodes_per_type"], coord_scheme)
        if "radius" not in args_dict or "bandwidth" not in args_dict:
            raise ValueError("Radius and Bandwidth not provided for the random topology")
        radius = args_dict["radius"]
        for src_id in self.nodes:
            node = self.nodes[src_id]
            src_coords = node.coordinates
            for trg_id in self.nodes:
                target = self.nodes[trg_id]
                if node == target:
                    continue
                target_coords = target.coordinates
                distance = math.sqrt((src_coords[0] - target_coords[0])**2 + (src_coords[1] - target_coords[1])**2)
                if distance < radius:
                    link = Link(
                        id=f"{node.id}->{target.id}",
                        source=node.id,
                        target=target.id,
                        path_bandwidths=[args_dict["bandwidth"]],
                        min_bandwidth=args_dict["bandwidth"],
                        expected_rtt=0
                    )
                    self.add_link(link)
        # check if any node is not connected to any other node, connect it to the closest node
        for src_id in self.nodes:
            node = self.nodes[src_id]
            if src_id not in self.neighbours:
                min_distance = 10000
                closest_node = None
                for trg_id in self.nodes:
                    target = self.nodes[trg_id]
                    if node == target:
                        continue
                    target_coords = target.coordinates
                    distance = math.sqrt((node.coordinates[0] - target_coords[0])**2 + (node.coordinates[1] - target_coords[1])**2)
                    if distance < min_distance:
                        min_distance = distance
                        closest_node = target
                link = Link(
                    id=f"{node.id}->{closest_node.id}",
                    source=node.id,
                    target=closest_node.id,
                    path_bandwidths=[args_dict["bandwidth"]],
                    min_bandwidth=args_dict["bandwidth"],
                    expected_rtt=0
                )
                self.add_link(link)

    def generate_ring_topology(self, args_dict):
        # the ring topology needs the argument radius in the args_dict. All nodes are placed in a ring.
        # The links are created between nodes that are within the radius.
        if "radius" not in args_dict or "bandwidth" not in args_dict:
            raise ValueError("Radius and Bandwidth not provided for the ring topology")
        self.generate_nodes(args_dict["node_types"], args_dict["no_nodes_per_type"], "random")
        angle_update = 2 * math.pi / self.no_nodes
        coord_generator = RingCoordinateGenerator(100, 100, args_dict["radius"], angle_update)
        first_node = None
        last_node = None
        for src_id in self.nodes:
            node = self.nodes[src_id]
            # With the nodes placed one next to the other.
            if first_node is None:
                first_node = node
            node.coordinates = coord_generator.generate()
            if last_node is not None:
                link = Link(
                    id=f"{last_node.id}->{node.id}",
                    source=last_node.id,
                    target=node.id,
                    path_bandwidths=[args_dict["bandwidth"]],
                    min_bandwidth=args_dict["bandwidth"],
                    expected_rtt=0
                )
                self.add_link(link)
            last_node = node
        link = Link(
            id=f"{node.id}->{first_node.id}",
            source=node.id,
            target=first_node.id,
            path_bandwidths=[args_dict["bandwidth"]],
            min_bandwidth=args_dict["bandwidth"],
            expected_rtt=0
        )
        self.add_link(link)

    def generate_grid_topology(self, args_dict):
        # the grid_FedAvg topology needs the argument x_spacing and y_spacing in the args_dict. All nodes are placed in a grid_FedAvg.
        # The links are created between nodes that are within the radius.
        coord_scheme = GridCoordinateGenerator(args_dict["radius"], args_dict["radius"], args_dict["x_spacing"], args_dict["y_spacing"])
        if "x_spacing" not in args_dict or "y_spacing" not in args_dict or "bandwidth" not in args_dict:
            raise ValueError("X and Y spacing and Bandwidth not provided for the grid_FedAvg topology")
        self.generate_nodes(args_dict["node_types"], args_dict["no_nodes_per_type"], coord_scheme)
        for src_id in self.nodes:
            node = self.nodes[src_id]
            src_coords = node.coordinates
            for trg_id in self.nodes:
                target = self.nodes[trg_id]

                if node == target:
                    continue
                target_coords = target.coordinates
                distance_x = math.fabs(src_coords[0] - target_coords[0])
                distance_y = math.fabs(src_coords[1] - target_coords[1])

                if (distance_x <= args_dict["x_spacing"] and distance_y == 0) or (args_dict["y_spacing"] <= distance_y and distance_x == 0):
                    link = Link(
                        id=f"{node.id}->{target.id}",
                        source=node.id,
                        target=target.id,
                        path_bandwidths=[args_dict["bandwidth"]],
                        min_bandwidth=args_dict["bandwidth"],
                        expected_rtt=0
                    )
                    self.add_link(link)

    def generate_bridged_clusters_topology(self, args_dict):
        # Generate a topology with two clusters one with all the nucs close to each other and 80% of SBCs, the other with
        # only 20% of SBCs. There is only one connection between the clusters.
        total_sbcs = 0
        for node_type in args_dict["node_types"]:
            total_sbcs += args_dict["no_nodes_per_type"][node_type.type]
        total_sbcs -= args_dict["no_nodes_per_type"]["nuc"]
        if total_sbcs <= 0 :
            raise ValueError("Not enough SBCs to create a bridged cluster topology")

        total_sbc_in_first_cluster = round(0.4 * total_sbcs)
        coord_scheme = BiforcatedCoordinateGenerator(50, 100, args_dict["no_nodes_per_type"]["nuc"] + total_sbc_in_first_cluster)
        # Generate the nodes
        # Place NUCs
        for i in range(args_dict["no_nodes_per_type"]["nuc"]):
            node = copy.deepcopy(args_dict["node_types"][0])
            node.set_id("nuc", i)
            node.coordinates = coord_scheme.generate()
            self.add_node(node)
        # Place SBCs
        for no_type, node_prototype in zip(no_nodes_per_type, node_types):
            if no_type == "nuc":
                continue
            for i in range(no_nodes_per_type[no_type]):
                node = copy.deepcopy(node_prototype)
                node.set_id(no_type, i)
                node.coordinates = self.sample_next_coords(coord_scheme)
                self.add_node(node)
        # Create the links between the nodes. Link all nodes in the first coordinate scheme. Link all nodes in the second. Link the two closest nodes.
        # Note that you can separate them based on wether the x is greater than 50 or not.
        min_dist_between = 100000000
        min_src_node = None
        min_trg_node = None
        for src_id in self.nodes:
            node = self.nodes[src_id]
            src_coords = node.coordinates
            for trg_id in self.nodes:
                target = self.nodes[trg_id]
                if node == target:
                    continue
                target_coords = target.coordinates
                distance = math.sqrt((src_coords[0] - target_coords[0])**2 + (src_coords[1] - target_coords[1])**2)
                if src_coords[0] < 50 and target_coords[0] < 50:
                    link = Link(
                        id=f"{node.id}->{target.id}",
                        source=node.id,
                        target=target.id,
                        path_bandwidths=[args_dict["bandwidth"]],
                        min_bandwidth=args_dict["bandwidth"],
                        expected_rtt=0
                    )
                    self.add_link(link)
                elif src_coords[0] >= 50 and target_coords[0] >= 50:
                    link = Link(
                        id=f"{node.id}->{target.id}",
                        source=node.id,
                        target=target.id,
                        path_bandwidths=[args_dict["bandwidth"]],
                        min_bandwidth=args_dict["bandwidth"],
                        expected_rtt=0
                    )
                    self.add_link(link)
                else:
                    if distance < min_dist_between:
                        min_dist_between = distance
                        min_src_node = node
                        min_trg_node = target
        link = Link(
            id=f"{min_src_node.id}->{min_trg_node.id}",
            source=min_src_node.id,
            target=min_trg_node.id,
            path_bandwidths=[args_dict["bandwidth"]],
            min_bandwidth=args_dict["bandwidth"],
            expected_rtt=0
        )
        self.add_link(link)

    def generate_topology(self, mechanims="fully-connected", args_dict={}):

        if "node_types" not in args_dict or "no_nodes_per_type" not in args_dict:
            raise ValueError("Node types and number of nodes per type not provided")
        for node_type in args_dict["node_types"]:
            self.no_nodes += args_dict["no_nodes_per_type"][node_type.type]
        match mechanims:
            case "fully-connected":
                self.generate_fully_connected_topology(args_dict)
            case "random":
                self.generate_random_topology(args_dict)
            case "grid":
                self.generate_grid_topology(args_dict)
            case "ring":
                self.generate_ring_topology(args_dict)
            case "bridged-clusters":
                self.generate_bridged_clusters_topology(args_dict)
            case _:
                raise ValueError(f"Unknown mechanism: {mechanims}, known mechanisms are: fully-connected, random, grid_FedAvg, ring")

    def draw(self):
        """
        Draws the network with nodes and links using matplotlib.
        """
        plt.figure(figsize=(10, 8))

        # Draw nodes
        for node in self.nodes.values():
            x, y = node.coordinates
            plt.scatter(x, y, color="blue", s=100)  # Node as a blue dot
            plt.text(x + 2, y, f"{node.name}", fontsize=9)

        # Draw links
        for link in self.links.values():
            source_coords = self.nodes[link.source].coordinates
            target_coords = self.nodes[link.target].coordinates
            plt.plot(
                [source_coords[0], target_coords[0]],
                [source_coords[1], target_coords[1]],
                color="gray",
                linewidth=1,
            )

        plt.title("Network Topology")
        plt.xlabel("X Coordinate")
        plt.ylabel("Y Coordinate")
        plt.grid(True)
        plt.show()

    def __repr__(self):
        return f"Network(no_nodes={self.no_nodes}, nodes={len(self.nodes)}, links={len(self.links)})"

    def write_network_json(self, filename):
        """
        Writes a network topology in JSON format.

        Args:
            filename (str): The output filename for the JSON file.

        """
        network = {
            "nodes": {},
            "links": {
                "neighbours": {},
                "path_info": []
            }
        }

        # Populate nodes
        for src_id in self.nodes:
            node = self.nodes[src_id]
            network["nodes"][node.name] = {
                "id": node.id,
                "arch": node.arch,
                "name": node.name,
                "coordinates": node.coordinates,
                "capacity": {
                    "cpu_milis": node.capacity.cpu_milis,
                    "memory": node.capacity.memory
                },
                "labels": node.labels
            }

        # Populate links
        links = self.links
        for link_id in links:
            link = links[link_id]
            network["links"]["path_info"].append({
                "id": f"{link.source}->{link.target}",
                "source": link.source,
                "target": link.target,
                "path_bandwidths": link.path_bandwidths,
                "min_bandwidth": min(link.path_bandwidths),
                "expected_rtt": link.expected_rtt
            })



        # Add neighbours to the links
        for neigh in self.neighbours:
            network["links"]["neighbours"][neigh] = self.neighbours[neigh]
        # Write to file
        with open(filename, 'w') as f:
            json.dump(network, f, indent=2)




if __name__ == "__main__":

    topo_types = ["bridged-clusters"]
    no_sbcs = [5, 10, 15, 20]
    probability_of_sbc ={"rpi4": 0.33, "rpi5_6G": 0.33, "rpi5_8G": 0.33}
    no_nucs = [5]
    for topo_type in topo_types:
        for no_sbc in no_sbcs:
            for no_nuc in no_nucs:
                total_nodes = no_sbc + no_nuc
                # Define the node types and their properties
                node_types = [
                    Node(
                        type="nuc",
                        arch="x86_64",
                        capacity=Capacity(cpu_milis=14800, memory=68719476736),
                        labels={"ether.edgerun.io/type": "sffc","ether.edgerun.io/model": "nuci5"},
                    ),

                    Node(
                        type="rpi4",
                        arch="arm32v7",
                        capacity=Capacity(cpu_milis=7200, memory=6442450944),
                        labels={"ether.edgerun.io/type": "sbc", "ether.edgerun.io/model": "rpi4"},
                    ),
                    Node(
                        type="rpi5_6G",
                        arch="arm32v7",
                        capacity=Capacity(cpu_milis=9600, memory=6442450944),
                        labels={"ether.edgerun.io/type": "sbc", "ether.edgerun.io/model": "rpi5_6G"},
                    ),
                    Node(
                        type="rpi5_8G",
                        arch="arm32v7",
                        capacity=Capacity(cpu_milis=9600, memory=8589934592),
                        labels={"ether.edgerun.io/type": "sbc", "ether.edgerun.io/model": "rpi5_6G"},
                    )
                ]
                # Define the number of nodes per type
                no_rpi4 = round(probability_of_sbc["rpi4"] * no_sbc)
                no_rpi5_6G = round(probability_of_sbc["rpi5_6G"] * no_sbc)
                no_rpi5_8G = no_sbc - no_rpi4 - no_rpi5_6G
                no_nodes_per_type = {"rpi4": no_rpi4, "rpi5_6G": no_rpi5_6G, "rpi5_8G": no_rpi5_8G, "nuc": no_nuc}

                # Create a network object
                network = Network()

                # Generate a fully connected network topology
                network.generate_topology(topo_type, {
                # network.generate_topology("grid_FedAvg", {
                # network.generate_topology("ring", {
                # network.generate_topology("fully-connected", {
                    "node_types": node_types,
                    "no_nodes_per_type": no_nodes_per_type,
                    "bandwidth": 100,
                    "radius": 50,
                    "x_spacing": 10,
                    "y_spacing": 10
                })

                # Draw the network
                network.draw()

                # Write the network to a JSON file
                network.write_network_json(f"network_{topo_type}{total_nodes}.json")