package PeersimSimulator.peersim.SDN.Nodes.Events;

import PeersimSimulator.peersim.SDN.Records.LoseTaskInfo;
import PeersimSimulator.peersim.SDN.Tasks.ITask;

import java.util.List;

public class TaskOffloadEvent implements Message{
    int srcNode;
    int dstNode;

    List<String> successorIDs;

    double deadline;

    double minCompLoad;
    double maxCompLoad;

    int minSucc;
    int maxSucc;

    double arrivalTime;

    double completionRate;
    ITask task;

    public TaskOffloadEvent(int srcNode,
                            int dstNode,
                            List<String> successorIDs,
                            double deadline,
                            double minCompLoad,
                            double maxCompLoad,
                            int minSucc,
                            int maxSucc,
                            int arrivalTime,
                            double completionRate,
                            ITask task) {
        this.srcNode = srcNode;
        this.dstNode = dstNode;
        this.successorIDs = successorIDs;
        this.deadline = deadline;
        this.minCompLoad = minCompLoad;
        this.maxCompLoad = maxCompLoad;
        this.minSucc = minSucc;
        this.maxSucc = maxSucc;
        this.arrivalTime = arrivalTime;
        this.completionRate = completionRate;
        this.task = task;
    }
    public TaskOffloadEvent(int srcNode,
                            int dstNode,
                            LoseTaskInfo lti){
        this.srcNode = srcNode;
        this.dstNode = dstNode;
        this.successorIDs = lti.getSuccessorIDs();
        this.deadline = lti.getDeadline();
        this.minCompLoad = lti.getMinComputation();
        this.maxCompLoad = lti.getMaxComputation();
        this.minSucc = lti.getMinSuccessors();
        this.maxSucc = lti.getMaxSuccessors();
        this.arrivalTime = lti.getArrivalTime();
        this.completionRate = lti.getCompletionRate();
        this.task = lti.getTask();
    }
    public ITask getTask() {
        return task;
    }

    public void setTask(ITask task) {
        this.task = task;
    }

    public int getSrcNode() {
        return srcNode;
    }

    public void setSrcNode(int srcNode) {
        this.srcNode = srcNode;
    }

    public int getDstNode() {
        return dstNode;
    }

    public void setDstNode(int dstNode) {
        this.dstNode = dstNode;
    }

    public LoseTaskInfo asLoseTaskInfo(){
        return new LoseTaskInfo(
                this.getSuccessorIDs(),
                this.getTask(),
                this.getDeadline(),
                this.getMinComputation(),
                this.getMaxComputation(),
                this.getMinSuccessors(),
                this.getMaxSuccessors(),
                this.getArrivalTime(),
                this.getCompletionRate()
        );
    }

    public List<String> getSuccessorIDs() {
        return successorIDs;
    }

    public double getDeadline() {
        return deadline;
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

    public double getArrivalTime() {
        return arrivalTime;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    @Override
    public double getSize() {
        return task.getInputSizeBytes();
    }
}
