package PeersimSimulator.peersim.SDN.Tasks;

import PeersimSimulator.peersim.SDN.Util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Application {


    private Map<String, ITask> tasks;
    private Map<String, List<ITask>> predecessors;

    private Map<String, List<ITask>> successors;

    private Set<ITask> finishedSubtasks;


    private int deadline;

    private String appID;

    private int clientID;

    private int handlerID;

    private int aggregateTaskSize;

    private int aggregateProgress;

    private int initialDataSize;
    private int outputDataSize;

    private double minComputation;
    private double maxComputation;
    private int minSuccessors;
    private int maxSuccessors;
    private int arrivalTime;

    public Application(Map<String, ITask> tasks, Map<String, List<ITask>> predecessors, int deadline, String appID, int clientID, int initialDataSize, int outputDataSize) {
        this.tasks = tasks;
        this.predecessors = predecessors;
        this.deadline = deadline;
        this.appID = appID;
        this.clientID = clientID;
        this.initialDataSize = initialDataSize; // TODO this should be the input data size of the root.
        this.outputDataSize = outputDataSize; // TODO Learn this value from the leafs of the computation.

        finishedSubtasks = new HashSet<>();


        minComputation = Double.MAX_VALUE;
        minSuccessors = Integer.MAX_VALUE;
        maxComputation = -1;
        maxSuccessors = -1;
        for(String v : tasks.keySet()) {
            PeersimSimulator.peersim.SDN.Tasks.ITask task = tasks.get(v);
            aggregateTaskSize += task.getTotalInstructions();
            if(task.getTotalInstructions() > maxComputation) maxComputation = task.getTotalInstructions();
            if(task.getTotalInstructions() < minComputation) minComputation = task.getTotalInstructions();
            if(successors.get(v).size() > maxSuccessors) maxSuccessors = successors.get(v).size();
            if(successors.get(v).size() < minSuccessors) minSuccessors = successors.get(v).size();
        }

        aggregateProgress = 0;


    }

    private void generateExecutionOrder(){
       //TODO
    }

    public boolean subTaskCanAdvance(String task){
        if(!predecessors.containsKey(task)) {
            Log.err("Somehow we are checking the wrong application for a subtask! AppID" + this.appID + " TaskID: " + task );
            return false;
        }
        List<ITask> d = predecessors.get(task);
        for(ITask v: d){

        }
        return true;

    }

    public void addProgress(String subTaskFinished){
        ITask v = this.tasks.get(subTaskFinished);
        this.aggregateProgress += v.getTotalInstructions();
        finishedSubtasks.add(v);
    }


    public boolean isFinished(){
        return finishedSubtasks.size() == this.tasks.keySet().size(); // Note: TODO I should confirm what exactly each of this sizes returns.
    }

    public Map<String, List<ITask>> getPredecessors() {
        return predecessors;
    }

    public void setPredecessors(Map<String, List<ITask>> predecessors) {
        this.predecessors = predecessors;
    }

    public Map<String, List<ITask>> getSuccessors() {
        return successors;
    }

    public void setSuccessors(Map<String, List<ITask>> successors) {
        this.successors = successors;
    }

    public int getDeadline() {
        return deadline;
    }

    public void setDeadline(int deadline) {
        this.deadline = deadline;
    }

    public String getAppID() {
        return appID;
    }

    public void setAppID(String appID) {
        this.appID = appID;
    }

    public int getClientID() {
        return clientID;
    }

    public void setClientID(int clientID) {
        this.clientID = clientID;
    }

    public int getHandlerID() {
        return handlerID;
    }

    public void setHandlerID(int handlerID) {
        this.handlerID = handlerID;
    }

    public int getAggregateTaskSize() {
        return aggregateTaskSize;
    }

    public void setAggregateTaskSize(int aggregateTaskSize) {
        this.aggregateTaskSize = aggregateTaskSize;
    }

    public int getAggregateProgress() {
        return aggregateProgress;
    }

    public void setAggregateProgress(int aggregateProgress) {
        this.aggregateProgress = aggregateProgress;
    }

    public int getInitialDataSize() {
        return initialDataSize;
    }

    public void setInitialDataSize(int initialDataSize) {
        this.initialDataSize = initialDataSize;
    }

    public double getMinComputation() {
        return minComputation;
    }

    public void setMinComputation(double minComputation) {
        this.minComputation = minComputation;
    }

    public double getMaxComputation() {
        return maxComputation;
    }

    public void setMaxComputation(double maxComputation) {
        this.maxComputation = maxComputation;
    }

    public int getMinSuccessors() {
        return minSuccessors;
    }

    public void setMinSuccessors(int minSuccessors) {
        this.minSuccessors = minSuccessors;
    }

    public int getMaxSuccessors() {
        return maxSuccessors;
    }

    public void setMaxSuccessors(int maxSuccessors) {
        this.maxSuccessors = maxSuccessors;
    }

    public int getOutputDataSize() {
        return outputDataSize;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(int arrivalTime) {
        this.arrivalTime = arrivalTime;
    }
}
