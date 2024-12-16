package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Nodes.Events.CloudInfo;

import java.util.List;

public record GlobalState(
        List<Integer> nodeIds,
        List<Integer> Q,
        List<Double> processingPowers,
        List<Integer> noCores,
        List<Integer> layers,
        List<Coordinates> positions,
        List<Double> bandwidths,
        List<Double> transmissionPowers,
        List<Double> averageCompletionTimes,
        List<Integer> droppedTasks,
        List<Integer> finishedTasks,
        List<Integer> totalTasks,
        List<Integer> isWorking,
        List<Double> energyConsumed,
        List<Integer> timesOverloaded,
        List<Integer> noFailedOnArrival,
        List<Integer> noExpired,

        CloudInfo cloudInfo
) {
}
