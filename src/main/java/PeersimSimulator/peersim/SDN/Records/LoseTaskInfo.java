package PeersimSimulator.peersim.SDN.Records;

import PeersimSimulator.peersim.SDN.Tasks.ITask;

import java.util.List;

public class LoseTaskInfo {

    ITask task;
    List<String> successorIDs;

    double deadline;

    double minCompLoad;
    double maxCompLoad;

    int minSucc;
    int maxSucc;

    int arrivalTime;

    double completionRate; // Snapshot of the moment the task was offloaded.


    public LoseTaskInfo(List<String> successorIDs,
                        ITask task,
                        double deadline,
                        double minCompLoad,
                        double maxCompLoad,
                        int minSucc,
                        int maxSucc,
                        int arrivalTime,
                        double completionRate
                        ) {
        this.successorIDs = successorIDs;
        this.task = task;
        this.deadline = deadline;
        this.minCompLoad = minCompLoad;
        this.maxCompLoad = maxCompLoad;
        this.minSucc = minSucc;
        this.maxSucc = maxSucc;
        this.arrivalTime = arrivalTime;
        this.completionRate = completionRate;
    }

    public double getMinComputation() {
        return minCompLoad;
    }

    public double getMaxComputation() {
        return maxCompLoad;
    }

    public int getMinSuccessors() {
        return minSucc;
    }

    public int getMaxSuccessors() {
        return maxSucc;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public double getDeadline() {
        return deadline;
    }

    public List<String> getSuccessorIDs() {
        return successorIDs;
    }

    public ITask getTask() {
        return task;
    }
}
