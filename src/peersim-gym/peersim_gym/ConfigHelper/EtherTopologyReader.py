"""
Usage:
To add tour own ether topology to PeersimGym, you must add the code that is in the Agent repository in the folder
"src/Utils/EtherTopologyGeneration" to the ether repository and run the "examples/MyNet.py". This will generate a .json
file, which should be moved back to the root of the agents repository. A topology for PeersimGym can then be generated
with the EtherTopologyReader.py and the values obtained feed to the config helper for PeersimGym to use the geneated
topology, the Readme.md in the PeersimGym repository has more information on how to do this. TODO actually add the information.
"""

import json

processing_power_key = 'cpu_milis'
capacity_key = 'capacity'
memory_key = 'memory'
coordinates_key = "coordinates"


def read_ether_topology(filename):
    with open(filename, "r") as f:
        topology = json.load(f)
    return topology


def get_projection_info(node_dict):
    #                  X             Y
    min_coords = [float("inf"), float("inf")]
    max_coords = [-float("inf"), -float("inf")]
    for node in node_dict.values():
        x = node["coordinates"][0]
        y = node["coordinates"][1]
        if x < min_coords[0]:
            min_coords[0] = x
        if y < min_coords[1]:
            min_coords[1] = y
        if x > max_coords[0]:
            max_coords[0] = x
        if y > max_coords[1]:
            max_coords[1] = y
    return min_coords, max_coords

def project_x(x, min_coords, max_coords, scale=100, project_coordinates=False):
    if not project_coordinates:
        return x
    return (x - min_coords[0]) / (max_coords[0] - min_coords[0]) * scale

def project_y(y, min_coords, max_coords, scale=100, project_coordinates=False):
    if not project_coordinates:
        return y
    return (y - min_coords[1]) / (max_coords[1] - min_coords[1]) * scale


def validate_coordinates(x, y, coord_dict):
    if (x, y) in coord_dict:
        print("DEBUG: There was a hit, two nodes are overlapping.")
        return validate_coordinates(x + 1, y + 1, coord_dict)
    else:

        coord_dict[(x, y)] = True
        return x, y




def get_topology_data(filename="SimpleNetwork_data.json", project_coordinates=False, expected_task_size=-1, skip_cloudlet=False):
    """
    This method reads the topology from the file filename json file and returns a dictionary with the following
    information:
    - positions: A string with the x,y coordinates of the nodes separated by ";"
    - memories: A string with the memory of the nodes separated by ";"
    - processing_powers: A string with the processing power of the nodes separated by ";"
    - cores: A string with the number of cores of the nodes separated by ";"
    - topology: A string with the topology of the network, where each node is separated by ":" and the neighbours are
    separated by ",".

    :param filename: The name of the file with the topology
    :param project_coordinates: If the coordinates should be projected to a 100x100 square
    :param expected_task_size: The expected size of the tasks, used to convert the memory of the nodes into the number of
    tasks that can be stored in the memory.
    :return:
    """
    topology = read_ether_topology(filename)
    node_dict = topology["nodes"]
    link_dict = topology["links"]

    cloudlet_id = None
    connects_to_server = []

    min_coords, max_coords = get_projection_info(node_dict)

    id_consultation_dict = {}
    node_positions = ""
    node_processing_powers = ""
    node_cores = ""
    node_memories = ""
    node_number = len(node_dict)
    layer_number = node_number
    nodes_per_layer = []
    layers_that_get_tasks = []
    q_max_per_layer_array = []
    freqs_per_layer_array = []
    no_cores_per_layer_array = []
    variations_per_layer_array = []
    controllers = []
    global_controller = None

    coord_dict = {}  # Laughs in 32 gb of ram and yet another datastructure
    for idx, key in enumerate(node_dict.keys()):
        node = node_dict[key]
        id_consultation_dict[node["id"]] = idx
        x = project_x(node["coordinates"][0], min_coords, max_coords, project_coordinates=project_coordinates)
        y = project_y(node["coordinates"][1], min_coords, max_coords, project_coordinates=project_coordinates)
        x, y = validate_coordinates(x, y, coord_dict)
        node_positions += f"{x},{y};"

        freq = node['capacity'][processing_power_key]*1000  # instructions in #CPU cycles/millisecond, we wish seconds
        node_processing_powers += f"{freq};"
        freqs_per_layer_array.append(freq)

        q_max = int(round(node['capacity'][memory_key]/expected_task_size)) if expected_task_size > 0 else node['capacity'][memory_key]
        node_memories += f"{q_max};"
        q_max_per_layer_array.append(q_max)

        node_cores += f"1;"
        no_cores_per_layer_array.append(1)

        variations_per_layer_array.append(0)

        nodes_per_layer.append(1)
        if "rpi" in key: # TODO: This is a temporary solution.
            layers_that_get_tasks.append(idx)
        if "rpi" in key or "nuc" in key or "server" in key:
            controllers.append(str(idx))

        if "nuc" in key:
            connects_to_server.append(node["id"])

        if "server" in key:
            global_controller = idx
            cloudlet_id = node["id"]


    node_positions = node_positions[:-1]  # Clip the last ";"
    node_processing_powers = node_processing_powers[:-1]
    node_cores = node_cores[:-1]
    node_memories = node_memories[:-1]

    link_topology = ""
    neighbours_per_node = {id_consultation_dict[k]: [] for k in id_consultation_dict.keys()}
    neighbours = link_dict["neighbours"]
    link_info = link_dict["path_info"]
    for node in neighbours.keys():
        # Skip cloudlet connection if flag set
        if cloudlet_id is not None and node == str(cloudlet_id) and skip_cloudlet:
            # Only add the servlet to its own neighbourhood
            source = id_consultation_dict[int(node)]
            target = id_consultation_dict[int(node)]
            if source not in neighbours_per_node[target]:
                neighbours_per_node[target].append(source)
            if target not in neighbours_per_node[source]:
                neighbours_per_node[source].append(target)
            for id in connects_to_server:
                source = id_consultation_dict[int(node)]
                target = id_consultation_dict[int(id)]
                if source not in neighbours_per_node[target]:
                    neighbours_per_node[target].append(source)
                if target not in neighbours_per_node[source]:
                    neighbours_per_node[source].append(target)
            continue
        neighbourhood = neighbours[node]
        source = id_consultation_dict[int(node)]
        for neighbour in neighbourhood:

            if cloudlet_id is not None and neighbour == cloudlet_id and skip_cloudlet:
                continue

            target = id_consultation_dict[neighbour]
            if source not in neighbours_per_node[target]:
                neighbours_per_node[target].append(source)
            if target not in neighbours_per_node[source]:
                neighbours_per_node[source].append(target)

    link_topology = ""
    for key in sorted(neighbours_per_node.keys()):
        value = neighbours_per_node[key]
        link_topology += f"{key}," + ",".join([str(x) for x in value]) + ";"
    link_topology = link_topology[:-1]
    result_dict = {
        "positions": node_positions,
        "memories": node_memories,
        "processing_powers": node_processing_powers,
        "cores": node_cores,
        "topology": link_topology,
        "number_of_layers": layer_number,
        "number_of_nodes": node_number,
        "nodes_per_layer": nodes_per_layer,
        "layers_that_get_tasks": layers_that_get_tasks,
        "q_max_per_layer_array": q_max_per_layer_array,
        "freqs_per_layer_array": freqs_per_layer_array,
        "no_cores_per_layer_array": no_cores_per_layer_array,
        "variations_per_layer_array": variations_per_layer_array,
        "client_layers": layers_that_get_tasks,
        "controllers": controllers,
        "channel_types_per_layer": [[0]*layer_number]*layer_number,
        "global_controller": global_controller,
    }
    return result_dict
