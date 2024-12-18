package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Nodes.Events.CloudInfo;

import java.util.List;

public record GlobalState(
        List<Integer> nodeIds,
        List<Integer> Q,
        List<Double> processingPowers,
        List<Integer> noCores,
        List<Integer> layers,
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
        List<Integer> totalTasksReceived,
        List<Integer> totalTasksProcessedPerNode,
        List<Integer> totalOffloadedTasksPerNode,
        List<Coordinates> positions,
        CloudInfo cloudInfo
) {
//    public boolean validState(){
//        int totalDroped_arrival = 0;
//        int totalDroped_expired = 0;
//        int totalFinished = 0;
//        int totalTasks = 0;
//        int totalDroped = 0;
//        int inQueue = 0;
//        for (int i = 0; i < this.averageCompletionTimes.size(); i++) {
//            totalFinished += this.finishedTasks.get(i);
//            totalTasks += this.totalTasks.get(i);
//            totalDroped += this.droppedTasks.get(i);
//            inQueue += this.Q.get(i);
//        }
//        for (int j = 0; j < this.noExpired.size(); j ++){
//            totalDroped_arrival += this.noFailedOnArrival.get(j);
//            totalDroped_expired += this.noExpired.get(j);
//        }
//        boolean valid = totalFinished + totalDroped_expired + totalDroped_arrival + inQueue == totalTasks;
//        return valid;
//    }
}
