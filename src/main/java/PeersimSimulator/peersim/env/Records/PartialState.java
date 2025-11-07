package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;

import java.util.List;

public record PartialState(
        int nodeId,
        int queueSize,
        List<Integer> Q,
        List<Integer> freeSpaces,
        double processingPower,
        double averageWaitingTime,
        int layer,
        Coordinates position,
        List<Double> distancesToNeighbours,
        double bandwidth,
        List<Double> transmissionPower,

        int numberOfNeighbours,
        TaskInfo nextTask,
        List<TaskInfo> tasks,
        List<WorkerInfo> neighInfos
) {

}
