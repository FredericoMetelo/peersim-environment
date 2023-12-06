package PeersimSimulator.peersim.env.Tasks;

import PeersimSimulator.peersim.env.Util.Log;

import java.util.*;

public class Application {


    private Map<String, ITask> tasks;
    private Map<String, List<ITask>> predecessors;

    private Map<String, List<ITask>> successors;

    private Set<ITask> finishedSubtasks;

    private ITask firstTask;

    private double deadline;

    private String appID;

    private int clientID;

    private int handlerID;

    private double totalTaskSize;

    private double aggregateProgress;

    private double initialDataSize;
    private double outputDataSize;

    private double minComputation;
    private double maxComputation;
    private int minSuccessors;
    private int maxSuccessors;
    private double arrivalTime;

    private List<ITask> expandedDAG;

    public Application(Map<String, ITask> tasks,
                       Map<String, List<ITask>> predecessors,
                       Map<String, List<ITask>> successors,
                       double deadline,
                       String appID,
                       int clientID,
                       double initialDataSize,
                       double outputDataSize,
                       ITask firstTask,
                       List<ITask> expandedDAG) {
        this.tasks = tasks;
        this.predecessors = predecessors;
        this.successors = successors;
        this.deadline = deadline;
        this.appID = appID;
        this.clientID = clientID;
        this.initialDataSize = initialDataSize; // TODO this should be the input data size of the root.
        this.outputDataSize = outputDataSize; // TODO Learn this value from the leafs of the computation.
        this.firstTask = firstTask;

        finishedSubtasks = new HashSet<>();
        this.expandedDAG = expandedDAG;


        minComputation = Double.MAX_VALUE;
        minSuccessors = Integer.MAX_VALUE;
        maxComputation = -1;
        maxSuccessors = -1;
        for(String v : tasks.keySet()) {
            PeersimSimulator.peersim.env.Tasks.ITask task = tasks.get(v);
            totalTaskSize += task.getTotalInstructions();
            if(task.getTotalInstructions() > maxComputation) maxComputation = task.getTotalInstructions();
            if(task.getTotalInstructions() < minComputation) minComputation = task.getTotalInstructions();
            List<ITask> t = successors.get(v);
            // Tasks may neither have successors nor predecessors (I.E. The first and last tasks per definition).
            if(t != null && successors.get(v).size() > maxSuccessors) maxSuccessors = successors.get(v).size();
            if(t != null && successors.get(v).size() < minSuccessors) minSuccessors = successors.get(v).size();
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
            if(!v.done()) return false;
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

    public double getDeadline() {
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

    public double getTotalTaskSize() {
        return totalTaskSize;
    }

    public void setTotalTaskSize(double totalTaskSize) {
        this.totalTaskSize = totalTaskSize;
    }

    public double getAggregateProgress() {
        return aggregateProgress;
    }

    public void setAggregateProgress(double aggregateProgress) {
        this.aggregateProgress = aggregateProgress;
    }

    public double getInitialDataSize() {
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

    public double getOutputDataSize() {
        return outputDataSize;
    }

    public double getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(double arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public double getCompletionRate(){
        return (double) aggregateProgress /totalTaskSize;
    }

    public ITask getFirstTask() {
        return firstTask;
    }

    /**
     * This method creates and returns an array, where a node that has dependencies on another will show up
     * afterwards.
     *
     * It might be interesting to expand this once in the beginning, as this is expanded once and only once.
     *
     * I.E.
     * (1,2), (2,3), (2,4), (3,5) may be expanded to:
     * 1,2,3,4,5
     * @return a list of nodes where dependent nodes show after their dependee
     */
    public List<ITask> expandToList(){
        if(expandedDAG != null) return expandedDAG;

        Set<String> alreadyExpanded = new HashSet<>();
        List<ITask> expandedTasks = new LinkedList<>();

        // Add First Task to array
        alreadyExpanded.add(firstTask.getId());
        expandedTasks.add(firstTask);
        int index = 0;
        // Loop until all nodes are expanded:
        while(expandedTasks.size() < tasks.size() || index > expandedTasks.size()) {
            // Get next element from array
            ITask current = expandedTasks.get(index);
            index++;
            // Add Successors of element to Array
            List<ITask> succ = successors.get(current.getId());
            // Check if not already Expanded
            if(succ == null || succ.isEmpty()) continue;
            for (ITask t: succ) {
                if(!alreadyExpanded.contains(t.getId())) {
                    // Add Successor's ID's to Array
                    // Add Successor's ID's to Set
                    alreadyExpanded.add(t.getId());
                    expandedTasks.add(t);
                }
            }
        }
        return expandedTasks;

    }


    public void setExpandedDAG(List<ITask> expandedDAG) {
        this.expandedDAG = expandedDAG;
    }

    public int applicationSize(){
        return this.tasks.keySet().size();
    }

    @Override
    public String toString() {
        return "Application{" +
                "tasks=" + tasks +
                ", appID=" + appID +
                ", arrivalTime=" + arrivalTime +
                ", deadline=" + deadline +
                ", finTasks=" + finishedSubtasks.size() +
                ", clientID=" + clientID +
                ", handlerID=" + handlerID +
                ", totalTaskSize=" + totalTaskSize +
                ", aggregateProgress=" + aggregateProgress +
                '}';
    }
}
