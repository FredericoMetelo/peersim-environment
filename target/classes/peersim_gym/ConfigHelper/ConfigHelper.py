import json
import math
from asyncio import sleep

import numpy as np
"""
Class with utility functions for generating the Schedules and configuration files for the PeersimGym Enviroment.
"""

# def linearSchedule(total_time, no_schedules):
#     """
#     This function generates a linear schedule for the task arrival rate. The task arrival rate increases linearly
#     from 1 to 10 over the total time specified. The rate is increased by 1 every 100 time units. The schedule is duplicated
#     no_schedule times.
#
#     :param total_time: Total time for the simulation
#     :param no_schedules: Number of schedules to generate.
#     :return: tuple(string, string) with the scheduled string in a Peersimgym understandable format - And the schedule name.
#     """
#     schedule_info = "Linear"
#     max_rate = 10
#     rate = 1.0
#     slot_time = 100
#     schedule = ""
#     curr_time = 0
#     while curr_time < total_time:
#         schedule += f"{rate}-{slot_time}"
#         curr_time += slot_time
#         rate += 1
#         if rate > max_rate:
#             rate = max_rate
#         if curr_time < total_time:
#             schedule += ","
#
#     list_of_schedules = [schedule] * no_schedules # Yes, this was done close to the deadline.
#     schedules = ""
#     for schedule in list_of_schedules:
#         schedules += schedule + ";"
#     schedules = schedules[:-1]
#     return schedules, schedule_info

def linearSchedule(total_time, no_schedules):
    """
    This function generates a linear schedule for the task arrival rate. The task arrival rate increases linearly
    from 1 to 10 over the total time specified. The rate is increased by 1 every 100 time units. The schedule is duplicated
    no_schedule times.

    :param total_time: Total time for the simulation
    :param no_schedules: Number of schedules to generate.
    :return: tuple(string, string) with the scheduled string in a Peersimgym understandable format - And the schedule name.
    """
    schedule_info = "Linear"
    max_rate = 10
    rate = 1.0
    slot_time = 100
    schedule = ""
    curr_time = 0
    while curr_time < total_time:
        schedule += f"{rate}-{slot_time}"
        curr_time += slot_time
        rate += 1
        if rate > max_rate:
            rate = max_rate
        if curr_time < total_time:
            schedule += ","

    list_of_schedules = [schedule] * no_schedules # Yes, this was done close to the deadline.
    schedules = ""
    for schedule in list_of_schedules:
        schedules += schedule + ";"
    schedules = schedules[:-1]
    return schedules, schedule_info


def zigzagSchedule(total_time, no_schedules):
    """
    This function generates a zigzag schedule for the task arrival rate. The task arrival rate increases linearly
    from 1 to 10 every 100 time units. Then decreases back to 1 over 100 time units. This cycle is repeated until simulation time
    is fully covered. The schedule is duplicated no_schedule times.
    :param total_time: Total time for the simulation
    :param no_schedules: Number of schedules to generate.
    :return: tuple(string, string) with the scheduled string in a Peersimgym understandable format - And the schedule name.
    """
    schedule_info = "Zigzag"
    max_rate = 10
    rate = 1.0
    slot_time = 100
    schedule = ""
    curr_time = 0
    direction = 1
    while curr_time < total_time:
        schedule += f"{rate}-{slot_time}"
        curr_time += slot_time
        rate += direction
        if rate > max_rate:
            rate = max_rate
            direction = -1
        if rate < 1:
            rate = 1
            direction = 1
        if curr_time < total_time:
            schedule += ","

    list_of_schedules = [schedule] * no_schedules # Yes, this was done close to the deadline.
    schedules = ""
    for schedule in list_of_schedules:
        schedules += schedule + ";"
    schedules = schedules[:-1]
    return schedules, schedule_info

def uniformRandomSchedule(total_time, no_schedules):
    """
    This function generates a random schedule for the task arrival rate. The task arrival rate is randomly selected between
    1 and 10 every 100 time units. The schedule is duplicated no_schedule times.
    :param total_time: Total time for the simulation
    :param no_schedules: Number of schedules to generate.
    :return: tuple(string, string) with the scheduled string in a Peersimgym understandable format - And the schedule name.
    """
    schedule_info = "UniformRandom"
    max_rate = 10
    rate = np.random.uniform(1, max_rate)
    slot_time = 100
    schedule = ""
    curr_time = 0
    while curr_time < total_time:
        schedule += f"{rate}-{slot_time}"
        curr_time += slot_time
        rate = np.random.uniform(1, max_rate)
        if curr_time < total_time:
            schedule += ","

    list_of_schedules = [schedule] * no_schedules  # Yes, this was done close to the deadline.
    schedules = ""
    for schedule in list_of_schedules:
        schedules += schedule + ";"
    schedules = schedules[:-1]
    return schedules, schedule_info


def heterogeneousRandomSchedule(total_time, no_schedules):
    """
    This function generates a random schedule for the task arrival rate. The task arrival rate is randomly selected between
    1 and 10 every 100 time units. A different schedule is generated no_schedule times.
    :param total_time: Total time for the simulation
    :param no_schedules: Number of schedules to generate.
    :return: tuple(string, string) with the scheduled string in a Peersimgym understandable format - And the schedule name.
    """
    schedule_info = "HeterogeneousUniform"
    max_rate = 10
    slot_time = 100
    list_of_schedules = []
    for i in range(no_schedules):
        schedule = ""
        curr_time = 0
        rate = np.random.uniform(1, max_rate)
        while curr_time < total_time:
            schedule += f"{rate}-{slot_time}"
            curr_time += slot_time
            rate = np.random.uniform(1, max_rate)
            if curr_time < total_time:
                schedule += ","
        list_of_schedules.append(schedule)
    schedules = ""
    for schedule in list_of_schedules:
        schedules += schedule + ";"
    schedules = schedules[:-1]
    return schedules, schedule_info

def heterogeneousZigzagSchedule(total_time, no_schedules):
    """
    This function generates a random schedule for the task arrival rate. The task arrival rate is randomly selected between
    1 and 10 every 100 time units. A different schedule is generated no_schedule times.
    :param total_time: Total time for the simulation
    :param no_schedules: Number of schedules to generate.
    :return: tuple(string, string) with the scheduled string in a Peersimgym understandable format - And the schedule name.
    """
    schedule_info = "HeterogeneousZigzag"
    max_rate = 10
    slot_time = 100
    list_of_schedules = []
    for i in range(no_schedules):
        schedule = ""
        rate = np.random.uniform(1, max_rate)
        curr_time = 0
        direction = 1
        while curr_time < total_time:
            schedule += f"{rate}-{slot_time}"
            curr_time += slot_time
            rate += direction
            if rate > max_rate:
                rate = max_rate
                direction = -1
            if rate < 1:
                rate = 1
                direction = 1
            if curr_time < total_time:
                schedule += ","
        list_of_schedules.append(schedule)
    schedules = ""
    for schedule in list_of_schedules:
        schedules += schedule + ";"
    schedules = schedules[:-1]
    return schedules, schedule_info


def sanityCheckSchedule(schedules, no_layers, action_time):
    """
    This function checks if the schedules are of the right format and if the total time in the schedule is equal to the
    action time. If the total time in the schedule is not equal to the action time then a warning is printed and the last
    schedule will be used by PeersimGym for the remainder of the time on the given layer that gets tasks.
    :param schedules:
    :param no_layers:
    :param action_time:
    :return:
    """
    schedules = schedules.split(";")
    if len(schedules) != no_layers:
        raise Exception("Number of schedules does not match number of layers")
    average_task_arrival_for_all = 0
    total_time_in_schedule = 0 # Note if smaller than the action time then the last schedule will be used. For the remainder of the time.
    for j in range(len(schedules)):
        schedule = schedules[j].split(",")
        total_time_in_schedule = 0
        average_task_arrival_for = 0
        for entry in schedule:
            if len(entry.split("-")) != 2:
                raise Exception("Schedule is not of the right format")
            time = int(entry.split("-")[1])
            average_task_arrival_for += float(entry.split("-")[0]) * time
            total_time_in_schedule += time
            if time < 0:
                raise Exception("Time in schedule cannot be negative")
        average_task_arrival_for_all += average_task_arrival_for/total_time_in_schedule
        if total_time_in_schedule != action_time:
            print(f"Warning: Total time in schedule {total_time_in_schedule} is not equal to action time (simulation: {total_time_in_schedule}/schedule: {action_time}). Last entry in schedule will be used for the remainder of the time on layer that gets tasks {j}")
            sleep(1)
    return average_task_arrival_for_all/no_layers


def minimumTaskProcessingPower(taskArrivalRate, taskTypeProbabilities, taskSizes, expectedSessions, maxTimeSteps,
                               noCores=4):
    """
    This function helps compute the minimum number of processing power to clear all tasks arriving in the system
    at a time-step before a specified number of time-steps.

    :param taskArrivalRate: The rate at which tasks arrive in the system, the arrival rate of a Poisson Process.
    :param taskSizes: The size of the tasks arriving in the system. Can be an array of sizes if there are multiple task types.
    :param taskTypeProbabilities: The probabilities of each task type arriving whenever a task arrives in the system.
    :param : The expected number of neighbours each node has.
    :param maxTimeSteps: The maximum number of time-steps to consider.
    :param noCores: The Cores the CPU's will have.
    :return: The processing power per CPU required to clear all tasks arriving in the system at a time-step before a specified
    """

    # From the task rates get the mean number of tasks arriving per time-step. Because the picking of a task type is
    # independent of the task arrival we have P_taskType_arriving = P_taskType_arriving * P_task_arriving
    meanTaskTypesArriving = taskArrivalRate * taskTypeProbabilities
    meanNumberOfInstructionsPerClient = np.sum(meanTaskTypesArriving) / taskTypeProbabilities.shape[0]

    # To process the expected number of tasks arriving we have in the time specified we have to process:
    requiredInstructionsPerTimeStep = (meanNumberOfInstructionsPerClient * expectedSessions) / (noCores * maxTimeSteps)

    # This is me confirming I didn't make a blunder. If I have meanNumberOfInstructionsPerClient total expected
    # instructions to process and I have maxTimeSteps to do it, then I need to process
    # meanNumberOfInstructionsPerClient/maxTimeSteps per time-step. But if I have noCores cores per CPU to process
    # the tasks, then I need to process meanNumberOfInstructionsPerClient/(maxTimeSteps*noCores) per core per time-step.

    return requiredInstructionsPerTimeStep


def create_array_with_all_entries_for_property_in_dict(property, dataset):
    """
    This function creates an array with all the entries for a property in a dataset. As in this function will return
    an array with all the values of the property in the dataset. This is useful for when you want to get all the values
    of a property in a dataset. Hope it's clear that this method simply returns an array with all the values of a property
    in a dataset. I can further elaborate if needed.


    :param property: The property to get all the values for in the dataset.
    :param dataset: The dataset to get the values from.
    :return: An array with all the values of the property in the dataset.
    """
    return [entry[property] for entry in dataset]


def import_string_to_float_array(data_string):
    return [float(value) for value in data_string.split(';')]


def import_string_to_int_array(data_string):
    return [int(value) for value in data_string.split(';')]


def taskArrivalForOccupancy(occupancy, taskTypeProbabilities, taskSizes, numClients, targetTimeForOccupancy,
                            processingPower, maxQueueSize):
    """
    Want to compute the average task arrival for a node to be at a certain occupancy level at a certain time-step. Given
    the task sizes, number of clients and the worker's processing power.
    O - Occupancy
    lambda - Task arrival Rate
    P - Processing power
    c - Number of clients
    Qt - Queue size at time t
    Qm - Maximum queue size
    T - Average Task Size

    Occupancy = Qt/Qm - Percentage of the queue filled

    The Q size vaires from time-step to time step by:
    Qt = Qt-1 + lambda*c - T/P

    therefore:
    Qn = n(lambda*c - T/P) + Q0, but, because Q0 = 0 we have Qn = n(lambda*c - T/P)

    Therfore if we want to have an occupancy of O at time-step n we have:
    Qn = O*Qm = n(lambda*c - T/P) (=) lambda = (O*Qm/n + T/P)/c
    :param occupancy:
    :param taskTypeProbabilities:
    :param taskSizes:
    :param numClients:
    :param targetTimeForOccupancy:
    :param processingPower:
    :return: lambda - The task arrival rate required to have an occupancy of O at time-step n
    """
    # Get the mean task size
    meanTaskSize = np.sum(taskSizes * taskTypeProbabilities)
    lam = (occupancy * maxQueueSize / targetTimeForOccupancy + meanTaskSize / processingPower) / numClients

    if lam > 1:
        print("Warning: Lambda is 1, this means the specifications are not possible")

    return min(lam, 1)


def average_points_in_circle(radius, num_generated_points):
    """
     A uniform distribution has equal probability of creating a point for every coordinate in a circle. Therefore, the
     chance of it being inside the communications radius is the area of the circle divided by the area of the square.
     But this value is only for a node in the middle of the square, the other extreme would be a point that is in one of
     the corners, this circle would have 1/4 of the area of the circle in the middle inside the square. Therefore, to
     account for this we average the expected number of nodes on a node on each corner and in the middle.
    :param radius:
    :param num_generated_points:
    :return:
    """
    total_area = 10000
    circle_area = math.pi * radius ** 2
    probability_point_inside = circle_area / total_area
    average_points_from_middle = num_generated_points * probability_point_inside
    average_points_from_corner = average_points_from_middle / 2
    average_points_inside = average_points_from_middle * 4 / 5
    return round(average_points_inside)


def load_json_to_dict(workloadPath):
    # Load the json file to a dictionary
    with open(workloadPath) as f:
        data = json.load(f)

    return data


def sanityCheckConfig(configs):
    """
    This function sanity checks the configuration dictionary to ensure that the parameters are consistent with each other
    and form a valid configuration for PeersimGym. The function will raise an exception if the parameters are not consistent.
    :param configs:
    :return:
    """
    # Rule 1: Guarantee that manual config is set with
    if configs["MANUAL_CONFIG"] == "True":
        if configs["MANUAL_CORES"] == "" or configs["MANUAL_FREQS"] == "" or configs["MANUAL_QMAX"] == "":
            raise Exception("Manual config is set but no values are given for the manual configuration")
        # Cannot use randomized topology and positions with manual configuration
        if configs["RANDOMIZETOPOLOGY"] == "True" or configs["RANDOMIZEPOSITIONS"] == "True":
            raise Exception("Cannot use randomized topology and positions with manual configuration")
    # Rule 2: The number of layers that get tasks is matched with the number of schedules.
    if len(configs["layersThatGetTasks"].split(",")) != len(configs["schedules"].split(";")):
        raise Exception("Number of layers that get tasks does not match the number of schedules")

    # Rule 3: All nodes must have a position
    if configs["RANDOMIZEPOSITIONS"] == "False":
        if len(configs["init.Net0.POSITIONS"].split(";")) != int(configs["SIZE"]):
            raise Exception("All nodes must have a position")
    # Rule 6: All nodes must have a topology
    if configs["RANDOMIZETOPOLOGY"] == "False":
        if len(configs["init.Net1.TOPOLOGY"].split(";")) != int(configs["SIZE"]):
            raise Exception("All nodes must have a topology")

def generate_config_dict(controllers="[0]",
                         size=10,
                         simulation_time=1000,
                         radius=50,
                         frequency_of_action=1,

                         has_cloud=1,
                         cloud_VM_processing_power=1e8,
                         cloud_position=[300, 300],
                         cloud_no_vms=3,

                         nodes_per_layer=[10],
                         cloud_access=[0],
                         freqs_per_layer=[1e7],
                         no_cores_per_layer=[4],
                         q_max_per_layer=[50],
                         variations_per_layer=[0],
                         layersThatGetTasks=[0],

                         task_probs=[1],
                         task_sizes=[38],
                         task_instr=[4e7],
                         task_CPI=[1],
                         task_deadlines=[100],
                         lambda_task_arrival_rate=0.5,
                         schedule_task_arrival_rate=None,
                         target_time_for_occupancy=0.5,
                         scale=1,

                         comm_B=4,
                         comm_Beta1=0.001,
                         comm_Beta2=4,
                         comm_Power=20,

                         weight_utility=10,
                         weight_delay=1,
                         weight_overload=50,
                         no_op_reward=0,

                         RANDOMIZETOPOLOGY=True,
                         RANDOMIZEPOSITIONS=True,
                         POSITIONS="18.55895350495783,17.02475796027715;47.56499372388999,57.28732691557995;5.366872150976409,43.28729893321355;17.488160666668694,29.422819514162434;81.56549175388358,53.14564532018814;85.15660881172089,74.47408014762478;18.438454887921974,44.310130148722195;72.04311826903107,62.06952644109185;25.60125368295145,15.54795598202745;17.543669122835837,70.7258178169151",
                         TOPOLOGY="0,1,2,3,6,8;1,0,2,3,4,5,6,7,8,9;2,0,1,3,6,8,9;3,0,1,2,6,8,9;4,1,5,7;5,1,4,7;6,0,1,2,3,8,9;7,1,4,5;8,0,1,2,3,6;9,1,2,3,6",
                         MANUAL_CONFIG=False,
                         MANUAL_CORES="1",
                         MANUAL_FREQS="1e7",
                         MANUAL_QMAX="10",
                         clientLayers="0",
                         defaultCPUWorkload="2.4e+9",
                         defaultMemoryWorkload="100",
                         workloadPath=None,
                         clientIsSelf=1,
                         channelTypes="PeersimSimulator.peersim.env.Transport.OpticalFiberSNR;PeersimSimulator.peersim.env.Transport.WirelessSNR",
                         channelTypesBetweenLayers="0,-1,-1,-1,-1,-1,-1,-1,1,-1,-1,1;-1,0,-1,-1,-1,-1,-1,-1,1,-1,-1,1;-1,-1,0,-1,-1,-1,-1,-1,1,-1,-1,1;-1,-1,-1,0,-1,-1,-1,-1,1,-1,-1,1;-1,-1,-1,-1,0,-1,-1,-1,1,-1,-1,1;-1,-1,-1,-1,-1,0,-1,-1,1,-1,-1,1;-1,-1,-1,-1,-1,-1,0,-1,1,-1,-1,1;-1,-1,-1,-1,-1,-1,-1,0,1,-1,-1,1;0,0,0,0,0,0,0,0,1,1,1,1;-1,-1,-1,-1,-1,-1,-1,-1,1,1,-1,1;-1,-1,-1,-1,-1,-1,-1,-1,1,-1,1,1;0,0,0,0,0,0,0,0,1,1,1,1",
                         snr="45",
                         energyCostComm="10",
                         energyCostComp="1",
                         ):
    """
    This function generates a configuration dictionary for the PeersimGym environment based on the parameters given. The
    dictionary will use the default values for the parameters that are not given. And lastly, the generated dictionary is sanity
    checked to ensure that the parameters are consistent with each other and form a valid configuration for PeersimGym.

    The dictionary is then returned.

    :param controllers:
    :param size:
    :param simulation_time:
    :param radius:
    :param frequency_of_action:
    :param has_cloud:
    :param cloud_VM_processing_power:
    :param cloud_position:
    :param cloud_no_vms:
    :param nodes_per_layer:
    :param cloud_access:
    :param freqs_per_layer:
    :param no_cores_per_layer:
    :param q_max_per_layer:
    :param variations_per_layer:
    :param layersThatGetTasks:
    :param task_probs:
    :param task_sizes:
    :param task_instr:
    :param task_CPI:
    :param task_deadlines:
    :param lambda_task_arrival_rate:
    :param schedule_task_arrival_rate:
    :param target_time_for_occupancy:
    :param scale:
    :param comm_B:
    :param comm_Beta1:
    :param comm_Beta2:
    :param comm_Power:
    :param weight_utility:
    :param weight_delay:
    :param weight_overload:
    :param no_op_reward:
    :param RANDOMIZETOPOLOGY:
    :param RANDOMIZEPOSITIONS:
    :param POSITIONS:
    :param TOPOLOGY:
    :param MANUAL_CONFIG:
    :param MANUAL_CORES:
    :param MANUAL_FREQS:
    :param MANUAL_QMAX:
    :param clientLayers:
    :param defaultCPUWorkload:
    :param defaultMemoryWorkload:
    :param workloadPath:
    :param clientIsSelf:
    :param channelTypes:
    :param channelTypesBetweenLayers:
    :param snr:
    :param energyCostComm:
    :param energyCostComp:
    :return:
    """
    if size != sum(nodes_per_layer):
        raise Exception("Size and sum of nodes per layer must be equal")
    # Press the green button in the gutter to run the script.
    total_cpu_cycles = [a * b for a, b in zip(task_CPI, task_instr)]
    avg_neighbours = average_points_in_circle(radius, nodes_per_layer[0])
    if isinstance(channelTypesBetweenLayers, list):
        channelTypesBetweenLayers = make_channel_types_between_layers(channelTypesBetweenLayers)
    if MANUAL_CONFIG:
        # Swap:
        #   freqs_per_layer := MANUAL_FREQS
        #   no_cores_per_layer := MANUAL_CORES
        #   q_max_per_layer := MANUAL_QMAX
        # These need to be converted to their array counterparts.
        freqs_per_layer = import_string_to_float_array(MANUAL_FREQS)
        no_cores_per_layer = import_string_to_int_array(MANUAL_CORES)
        q_max_per_layer = import_string_to_int_array(MANUAL_QMAX)

    # Preparing the schedules. Either a number is given or a function is given. The schedules are then generated.
    # schedule = [f"{lambda_task_arrival_rate}-{simulation_time}" for i in range(len(layersThatGetTasks))]
    schedule = ";".join(f"{lambda_task_arrival_rate}-{simulation_time}" for i in range(len(layersThatGetTasks)))

    schdule_info = "FixedLambda"+str(lambda_task_arrival_rate)
    if schedule_task_arrival_rate is not None and callable(schedule_task_arrival_rate):
        schedule,schedule_info = schedule_task_arrival_rate(simulation_time, len(layersThatGetTasks))
        lambda_task_arrival_rate = sanityCheckSchedule(schedule, len(layersThatGetTasks), simulation_time)
    print(f"Loading schedule: {schedule}")
    # TODO When randomizing seed, also re-run the scheduling mechanism to break the deterministic nature of the scheduling!!!
    if workloadPath is not None:
        # Need to:
        # dataset := load_json_to_dict(workloadPath)
        # task_sizes := mean(dataset)
        # task_instr :=
        # task_CPI :=

        # Begin by loading json data to datasets using json libary
        dataset = load_json_to_dict(workloadPath)

        task_CPI = []
        task_sizes = []
        task_instr = []
        for key, job in dataset.items():
            # for each job in the dataset, which is a dictionary, we get the values of the keys into

            cpu = job.get("max_cpu") / 100 * job.get("total_resources_duration") / 1000 * float(defaultCPUWorkload)
            mem = job.get("max_mem")*float(defaultMemoryWorkload)
            task_CPI.append(1)
            task_sizes.append(mem)
            task_instr.append(cpu)

        # Uniform probability for all tasks
        task_probs = [1 for _ in task_sizes] # Converting implicitly to task weights not probs

    # Scale deadlines - scaled in the env for consistency
    # task_deadlines = [deadline * scale for deadline in task_deadlines]

    configs = {
        "SIZE": str(size),
        "CYCLE": "1",
        "CYCLES": str(simulation_time),
        "random.seed": "1234567890",
        "MINDELAY": "0",
        "MAXDELAY": "0",
        "DROP": "0",
        "CONTROLLERS": make_ctr(controllers),
        "SCALE": str(scale),

        "CLOUD_EXISTS": str(has_cloud),
        "CLOUD_POS": to_string_array(cloud_position),
        "NO_LAYERS": str(len(nodes_per_layer)),
        "NO_NODES_PER_LAYERS": to_string_array(nodes_per_layer),
        "CLOUD_ACCESS": to_string_array(cloud_access),

        "FREQS": to_string_array(freqs_per_layer),
        "NO_CORES": to_string_array(no_cores_per_layer),
        "Q_MAX": to_string_array(q_max_per_layer),
        "VARIATIONS": to_string_array(variations_per_layer),

        "protocol.cld.no_vms": str(cloud_no_vms),
        "protocol.cld.VMProcessingPower": str(cloud_VM_processing_power),

        "init.Net1.r": str(radius),

        "utility_reward": weight_utility,
        "delay_weight": weight_delay,
        "overload_weight": weight_overload,
        "no_op_reward": no_op_reward,

        "protocol.mng.cycle": str(frequency_of_action),

        "protocol.clt.numberOfTasks": str(len(task_probs)),
        "protocol.clt.weight": to_string_array(task_probs, print_as_int=False),
        "protocol.clt.CPI": to_string_array(task_CPI),
        "protocol.clt.T": to_string_array(task_sizes),
        "protocol.clt.I": to_string_array(task_instr),
        "protocol.clt.taskArrivalRate": str(lambda_task_arrival_rate),
        "schedules": schedule,
        "protocol.clt.numberOfDAG": "1",
        "protocol.clt.dagWeights": "1",
        "protocol.clt.edges": "",
        "protocol.clt.minDeadline": to_string_array(task_deadlines),
        "protocol.clt.vertices": "1",
        "layersThatGetTasks": to_string_array(layersThatGetTasks),
        "clientIsSelf": str(clientIsSelf),

        "protocol.props.B": str(comm_B),
        "protocol.props.Beta1": str(comm_Beta1),
        "protocol.props.Beta2": str(comm_Beta2),
        "protocol.props.P_ti": str(comm_Power),
        "RANDOMIZEPOSITIONS": str(RANDOMIZEPOSITIONS),
        "init.Net0.POSITIONS": POSITIONS,

        "RANDOMIZETOPOLOGY": str(RANDOMIZETOPOLOGY),
        "init.Net1.TOPOLOGY": TOPOLOGY,

        "MANUAL_CONFIG": str(MANUAL_CONFIG),
        "MANUAL_CORES": MANUAL_CORES,
        "MANUAL_FREQS": MANUAL_FREQS,
        "MANUAL_QMAX": MANUAL_QMAX,
        "clientLayers": to_string_array(clientLayers),

        "protocol.clt.defaultCPUWorkload": defaultCPUWorkload,
        "protocol.clt.defaultMemoryWorkload": defaultMemoryWorkload,
        "protocol.clt.workloadPath": workloadPath,

        "protocol.urt.channelTypes": channelTypes,
        "protocol.urt.channelTypesBetweenLayers": channelTypesBetweenLayers,
        "protocol.urt.SNR": snr,
        "protocol.wrk.energyCostComm": energyCostComm,
        "protocol.wrk.energyCostComp": energyCostComp,
    }

    sanityCheckConfig(configs)
    return configs


# taskArrivalForOccupancy(expected_occupancy, np.array(task_probs), np.array(total_cpu_cycles),
#                                                                 avg_neighbours, simulation_time*target_time_for_occupancy,
#                                                                 total_cpu_cycles[0], q_max_per_layer[0])

def make_ctr(ctrs_list):
    return to_string_array(ctrs_list, ";")


def make_channel_types_between_layers(channel_types):
    """
    No this is not the most efficient way...
    """
    for i in range(len(channel_types)):
        channel_types[i] = to_string_array(channel_types[i], ",")
    return ";".join(channel_types)


def to_string_array(arr, separator=",", print_as_int=True):
    s = ""
    for i in range(len(arr)):
        s += str(int(arr[i]) if print_as_int else arr[i])
        if i < len(arr) - 1:
            s += separator
    return s


def get_lambda_and_schedule(task_arrival_rate) -> tuple:
    if isinstance(task_arrival_rate, str):
        print(f"Loading Schedule: {task_arrival_rate}")
        match task_arrival_rate:
            case "Linear":
                return linearSchedule, 1.0
            case "Zigzag":
                return zigzagSchedule, 1.0
            case "Uniform":
                return uniformRandomSchedule, 1.0
            case "HeteroUniform":
                return heterogeneousRandomSchedule, 1.0
            case "HeteroZigzag":
                return heterogeneousZigzagSchedule, 1.0
            case _:
                raise Exception("Invalid string for task arrival rate The options are an array with: 'linear', 'UniformRandom', 'ZigZag', 'HeteroUniformRandom', 'HeteroZigZag'")
    elif isinstance(task_arrival_rate, int) or isinstance(task_arrival_rate, float):
        print(f"Loading Schedule: Fixed_{task_arrival_rate}")
        return None, task_arrival_rate
    else:
        raise Exception("Did not specify neither a string (schedule type) nor an integer for the task arrival rate.  The options are a float or int for constant task arrival or a shcedule of the ones in array with: 'linear', 'UniformRandom', 'ZigZag', 'HeteroUniformRandom', 'HeteroZigZag'")
