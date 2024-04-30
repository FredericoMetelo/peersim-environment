package PeersimSimulator.peersim.env.Nodes.Workers;

import PeersimSimulator.peersim.env.Records.LoseTaskInfo;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.TaskHistory;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.env.Nodes.Events.*;

import java.util.*;

import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.transport.Transport;


public class DAGWorker extends AbstractWorker {


    //======================================================================================================
    // Variables
    //======================================================================================================


    // invariant: totalTasksReceived = totalTasksProcessed + totalTasksDropped + totalTasksOffloaded + getQueueSize()


    private double minCompLoad;
    private double maxCompLoad;
    private int minSucc;
    private int maxSucc;
    private double minArrivalTime;
    private double minCompletionRate;
    private double maxCompletionRate;

    private int tasksWithAllDepenciesMet;

    public DAGWorker(String prefix) {
        super(prefix);
        //======== Define the Constant values from Configs ===========//
        // TODO make this definable from the configs.
        // TODO in future have multiple classes of nodes?

        printParams();
        //======== Init Datastructures ===========//

        minCompLoad = Double.MAX_VALUE;
        maxCompLoad = Double.MIN_VALUE;

        minSucc = Integer.MAX_VALUE;
        maxSucc = Integer.MIN_VALUE;

        minArrivalTime = Integer.MAX_VALUE;

        minCompletionRate = Double.MAX_VALUE;
        maxCompletionRate = Double.MIN_VALUE;

        tasksWithAllDepenciesMet = 0;

    }


    //======================================================================================================
    // Methods
    //======================================================================================================

    /** Behaviour of this method:
     * 1. Process the currently selected task
     * 2. If there is no task currently selected, select one.
     * 3. When a task is selected, decide whether to offload it or not. This halts the progress of the simulation.
     * 4. Whenever the task being processed is finished:
     *      4.1 Process its removal from the relevant data-structures.
     *      4.2 If the task finishing completes an application finishing, inform the client of the finish.
     *      4.3 If the task finishing is handled by another node. Send the results to said node.
     * 5. After every X time-steps or if the node is idling with applications to be processed, rank all the tasks in
     * the node and add them to the queue.
     */
    @Override
    public void nextCycle(Node node, int protocolID) {
        if (!active) return;
        // Advance Task processing and update status.
        double remainingProcessingPower = processingPower;
        while (remainingProcessingPower > 0 && !this.idle()) {
            if (this.current == null || this.current.done()) {
                changedWorkerState = nextProcessableTask(node, pid);
                if (current == null) {
                    break;
                }
            }
            remainingProcessingPower = current.addProgress(remainingProcessingPower);
            if (current.done()) {
                totalTasksProcessed++;
                handleTaskConcludedEvent(node, pid, current);
                current = null;
            }
        }
        cleanExpiredApps();
        if ((CommonState.getTime() % RANK_EVENT_DELAY) == 0 || this.awaitingSerialization() ) { // && !this.recievedApplications.isEmpty() // if offloaded a task and does not run this then no cleanup.
            applicationSerialization();
        }

        // Then Communicate changes in Queue Size and Recieved Nodes  to BasicController.
        if (this.changedWorkerState) { // TODO Guarantee we inform neighbours. Guarantee no double offloading.
            broadcastStateChanges(node, protocolID);
        }
    }


    //======================================================================================================
    // Private Methods
    //======================================================================================================

    /**
     * This method is called whenever a task is finished. It handles the removal of the task from the relevant data
     * structures. If the task is handled by another node, it sends the results to said node. If the task is the last
     * task of an application, it informs the client of the application finish.
     * @param node The node that is currently running the simulation.
     * @param protocolID The protocol ID of the DAGWorker.
     * @param finishedTask The task that was finished.
     */
    @Override
    protected void handleTaskFinish(Node node, int protocolID, ITask finishedTask) {
        Application app = this.managedApplications.get(finishedTask.getAppID());
        app.addProgress(finishedTask.getId());
        finishedTask.addEvent(TaskHistory.TaskEvenType.COMPLETED, this.id, CommonState.getTime());
        tasksCompletedSinceLastCycle.add(finishedTask);
        if (app.isFinished()) {
            this.handleApplicationFinish(node, protocolID, app);
        }
    }


    @Override
    protected void handleTaskOffloadEvent(TaskOffloadEvent ev) {
        // Receive Tasks Event Receives a set of new Tasks
        // The new tasks are added to the receivedRequests. This
        // is reasonable because this requests can be consumed for
        // processing as well.
        if (this.getId() != ev.getDstNode()) {
            wrkErrLog(EVENT_OFFLOADED_TASKS_ARRIVED_AT_WRONG_NODE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID() +" arrivedAt=" + this.getId() + " supposedToArriveAt=" + ev.getDstNode());
            return;
        }
        ITask offloadedTask = ev.getTask();
        offloadedTask.addEvent(TaskHistory.TaskEvenType.OFFLOADED, this.id, CommonState.getTime());
        wrkInfoLog(EVENT_TASK_OFFLOAD_RECIEVE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID());
        if (this.getTotalNumberOfTasksInNode() > qMAX) {
            this.droppedLastCycle++;
            this.totalDropped++;
            Log.err("Dropping Tasks(" + this.droppedLastCycle + ") Node " + this.getId() + " is Overloaded!"); // TODO
        } else {
            LoseTaskInfo lti = ev.asLoseTaskInfo();
            double rank = remoteTaskRank(lti, offloadedTask);
            offloadedTask.setCurrentRank(rank);
            this.loseTaskInformation.put(offloadedTask.getId(), lti);
            this.queue.add(offloadedTask);

        }

        this.changedWorkerState = true;
    }

    /**
     * Ranks all tasks in the node and sort's them in a queue (except the one being currently processed).
     * If any applications have passed their deadline plus a grace period, this applications are removed and aren't
     * added to the queue.
     *
     */
    @Override
    protected void applicationSerialization() {

        if(recievedApplications.isEmpty()) return ;
        wrkInfoLog("SERIALIZING EVENT", "Q_size=" + this.queue.size() + " rcv_Apps=" + this.recievedApplications.size() + " working=" + !(current == null));
        TreeSet<ITask> newQ = new TreeSet<>(dependentTaskComparator);

        minCompLoad = Double.MAX_VALUE;
        maxCompLoad = Double.MIN_VALUE;
        minSucc = Integer.MAX_VALUE;
        maxSucc = Integer.MIN_VALUE;
        minArrivalTime = Integer.MAX_VALUE;
        minCompletionRate = Double.MAX_VALUE;
        maxCompletionRate = Double.MIN_VALUE;
        this.tasksWithAllDepenciesMet = 0;
        Map<String, Double> previousPrioritiesPerTask = new HashMap<>();
        if (current != null) previousPrioritiesPerTask.put(current.getId(), current.getCurrentRank());
        for (Application a : recievedApplications) {
            getDataForPriorityMetrics(a);
        }

        Iterator<ITask> qiterator = queue.iterator();
       // Set<Application> removeApps = new HashSet<>();
        while (qiterator.hasNext()) {
            ITask t = qiterator.next();
            Application a = managedApplications.get(t.getAppID());
            previousPrioritiesPerTask.put(t.getId(), t.getCurrentRank()); // Must have a previous rank to be in the queue.
            if (a != null) {
                getDataForPriorityMetrics(a);

            } else {
                LoseTaskInfo lti = loseTaskInformation.get(t.getId());
                getDataForPriorityMetrics(lti);
            }
        }

        // Cycle through queued tasks ranking them and re-inserting them in app
        for (ITask t : queue) {
            double rank;
            if (!isTaskLocal(t)) {
                rank = remoteTaskRank(loseTaskInformation.get(t.getId()), t);
                this.tasksWithAllDepenciesMet++;
            }else {
                Application app = managedApplications.get(t.getAppID());
                rank = localTaskRank(app, previousPrioritiesPerTask, t);
                if(app.subTaskCanAdvance(t.getId())) {
                    this.tasksWithAllDepenciesMet++;
                }
            }
            t.setCurrentRank(rank);
            newQ.add(t);
        }
        // Cycle through new applications ranking their every-tasks and re-inserting them in app # make shure to cycle through this in a way that all priorities are pre-computed.
        // Note: Add logic to deal with the initial task!!
        for (Application app : recievedApplications) {
            List<ITask> tasks = app.expandToList();
            for (ITask t : tasks) {
                double rank = localTaskRank(app, previousPrioritiesPerTask, t);
                t.setCurrentRank(rank);
                previousPrioritiesPerTask.put(t.getId(), rank);
                newQ.add(t);
            }
        }
        queue = newQ;
        resetReceived();
    }

    @Override
    void resetReceived() {
        recievedApplications = new LinkedList<>();
        toAddSize = 0;
    }
    private void getDataForPriorityMetrics(LoseTaskInfo lti) {
        getDataForPriorityMetrics(lti.getMaxComputation(), lti.getMinComputation(), lti.getMaxSuccessors(), lti.getMinSuccessors(), lti.getArrivalTime(), lti.getCompletionRate());
    }

    private void getDataForPriorityMetrics(Application a) {
        getDataForPriorityMetrics(a.getMaxComputation(), a.getMinComputation(), a.getMaxSuccessors(), a.getMinSuccessors(), a.getArrivalTime(), a.getCompletionRate());
    }

    private void getDataForPriorityMetrics(double maxComputation, double minComputation, int maxSuccessors, int minSuccessors, double arrivalTime, double completionRate) {
        if (maxComputation > maxCompLoad) maxCompLoad = maxComputation;
        if (minComputation < minCompLoad) minCompLoad = minComputation;
        if (maxSuccessors > maxSucc) maxSucc = maxSuccessors;
        if (minSuccessors < minSucc) minSucc = minSuccessors;
        if (arrivalTime < minArrivalTime) minArrivalTime = arrivalTime;
        if (completionRate > maxCompletionRate) maxCompletionRate = completionRate;
        if (completionRate < minCompletionRate) minCompletionRate = completionRate;
    }

    private double localTaskRank(Application app, Map<String, Double> currentPriorities, ITask task) {
        // Compute Parts of the ranking function TODO make this methods use the fields of the class instead of parameter variables...
        // Normalized Number of Successors
        // Last task must always have 0 successors
        // Normalized Computational Load:
            // Long story short: this was returning NaN when maxCompLoad = minCompLoad, the important part is the relation
            // between the values so if minCompLoad = maxCompLoad I just call it 0.
        // Normalized Number of Occurrences (IGNORED)
        // Normalized Urgency Metric
        // Normalized Completion Rate
        // Compute the ranking function
        List<ITask> succ = app.getSuccessors().get(task.getId());
        double En = succ.size();
        double normalized_En = (En) / (Math.max(this.maxSucc, 1)); // Avoid division by 0
        double Ln = 1 / task.getTotalInstructions();
        double normalized_Ln = (this.minCompLoad == this.maxCompLoad)? 0 : (Ln - 1 / this.minCompLoad) / (1 / this.maxCompLoad - 1 / this.minCompLoad);

        double urgency = -app.getDeadline() + CommonState.getTime() - app.getArrivalTime() + 1;
        double y_normalized = (urgency - this.minArrivalTime + 1) / (CommonState.getTime() - this.minArrivalTime + 1); // + 1 on both sides to avoid divisions by 0
        double normalized_Completion = (app.getCompletionRate() - this.minCompletionRate) / (this.maxCompletionRate - this.minCompletionRate);
        double succPriority = Double.NEGATIVE_INFINITY;
        List<ITask> pred = app.getPredecessors().get(task.getId());
        if (pred == null || pred.isEmpty()) {
            succPriority = 0;
        } else {
            for (ITask t : pred) {
                Double priority = currentPriorities.get(t.getId());
                if (priority == null) {
                    wrkErrLog("ERROR","This should never happen, all dependencies should be processed before the task is processed. But if this is logged then it is not happening");
                } else if (priority > succPriority) {
                    succPriority = priority;
                }
            }
        }
        return succPriority + normalized_Completion + normalized_En + normalized_En + y_normalized + normalized_Ln;
    }

    private double remoteTaskRank(LoseTaskInfo lti, ITask task) {
        // IMPORTANT: I will assume that only tasks that have their dependencies fulfilled are elegible for offloading.
        //  therefore no task that is offloaded will have dependencies on the offloaded node. (aka only tasks in
        //  "parallel/same layer" from one application may be offloaded).
        //  see localTaskRank for what each parameter does.
        List<String> succ = lti.getSuccessorIDs();
        double En = succ.size();
        double normalized_En = (En) /  (Math.max(this.maxSucc, 1));
        double Ln = 1 / task.getTotalInstructions();
        double normalized_Ln =  (this.minCompLoad == this.maxCompLoad)? 0 : (Ln - 1 / this.minCompLoad) / (1 / this.maxCompLoad - 1 / this.minCompLoad);
        double urgency = -lti.getDeadline() + CommonState.getTime() - lti.getArrivalTime() + 1;
        double y_normalized = (urgency - this.minArrivalTime + 1) / (CommonState.getTime() - this.minArrivalTime + 1);
        double normalized_Completion = (lti.getCompletionRate() - this.minCompletionRate) / (this.maxCompletionRate - this.minCompletionRate);
        double succPriority = 0; // I assume that all offloaded nodes have their priorities met, therefore there is no need to check for dependent nodes.

        return succPriority + normalized_Completion + normalized_En + normalized_En + y_normalized + normalized_Ln;
    }

    /**
     * This method selects the task to be processed next. Whenever a task is selected computation halts and awaits for
     * the decision whether to offload or not. This method will make the decision whether to offload or not for every
     * available task by recursively calling itself. If all the processable tasks in the queue are offloaded, this method
     * sets current null. The recursion halts if a task is picked to be processed locally.
     * @return Whether <code>this.current</code> changed value || if <code>changed <code/> is <code>true</code>
     */
    @Override
    protected boolean nextProcessableTask(Node node, int pid) {
        // Check if current task is done, if it isn't return immediately
        if (current != null && !current.done()) {
            return this.changedWorkerState;
        }
        if ((current == null || current.done()) && queue.isEmpty() && !recievedApplications.isEmpty()) {
            applicationSerialization();
        }
        this.current = this.selectNextTaskWithDependenciesMet(false);
        boolean taskAssigend = this.current != null;
        if(taskAssigend) {
            this.current.addEvent(TaskHistory.TaskEvenType.SELECTED_FOR_PROCESSING, this.id, CommonState.getTime());
            this.tasksToBeLocallyProcessed.remove(current.getId());
            this.tasksWithAllDepenciesMet--;
        }else{
            wrkInfoLog(EVENT_NO_TASK_PROCESS, "id="+this.getId());
        }
        return this.changedWorkerState;

    }

    /**
     * First see if there is any task with all dependencies met in the queue, else Idle and set current null.
     * @return <code>true</code> if a task was selected to current, <code>false</code> if the node is idling.
     */
    private ITask selectNextTaskWithDependenciesMet(boolean forOffloading) {
        Iterator<ITask> iterOverQueuedTasks = this.queue.descendingIterator();
        ITask selected = null;
        while (iterOverQueuedTasks.hasNext()) {
            ITask t = iterOverQueuedTasks.next();
            if (this.loseTaskInformation.containsKey(t.getId())) {
                selected= t;
                iterOverQueuedTasks.remove();
                this.changedWorkerState = true;
                break;
            }
            Application app = this.managedApplications.get(t.getAppID());
            // forOffloading => subTaskCanAdvance = !forOffloading v subTaskCanAdvance
            boolean canOffload = (!forOffloading ||
                    (app.subTaskCanAdvance(t.getId()) && !this.tasksToBeLocallyProcessed.contains(t.getId())));
            if (app != null && app.subTaskCanAdvance(t.getId()) && canOffload) {
                selected = t;
                iterOverQueuedTasks.remove();
                this.changedWorkerState = true;
                break;
            }
        }
        return selected;
    }



    @Override
    public boolean offloadInstructions(int pid, OffloadInstructions offloadInstructions) {
        if(this.awaitingSerialization()) applicationSerialization();
        BasicOffloadInstructions oi = (BasicOffloadInstructions) offloadInstructions;
        ITask task = this.selectNextTaskWithDependenciesMet(true);
        // ngl, it's late... There is for sure a better way of implementing this. This boolean overloading the method
        // does not look very good.
        if(task == null) {
            wrkInfoLog(EVENT_NO_TASK_OFFLOAD, "id="+this.getId());
            return false;
        }
        // TODO this is a problem, it works because I try to leave the self in position 0 in the linkable.
        //  It would not work with multiple SimulationManagers.

        if (oi.getNeighbourIndex() != 0) { // Self is always the first to be added to the linkable. And should not be changed.
/*
            if(this.queue.isEmpty() ){
                return false;
            }
*/
            Node node = Network.get(this.getId());
            int linkableID = FastConfig.getLinkable(pid);
            Linkable linkable = (Linkable) node.getProtocol(linkableID);
            if(!validTargetNeighbour(oi.getNeighbourIndex(), linkable)) {
                return false;
            }

            Node target = linkable.getNeighbor(oi.getNeighbourIndex());
            if ( !target.isUp()) {
                wrkErrLog(EVENT_ERR_NODE_OUT_OF_BOUNDS, "The requested target node is outside the nodes known by the DAGWorker="
                        + (oi.getNeighbourIndex() < 0 || oi.getNeighbourIndex() > linkable.degree())
                        + ". Or is down=" + target.isUp());
                return false;
            }

            LoseTaskInfo lti = getOrGenerateLoseTaskInfo(node, task);
            if (lti == null) {
                throw new RuntimeException("Something went wrong with tracking of lose tasks with loseTaskInfo. Killing the simulation.");
            }

            wrkInfoLog("OFFLOADING TASK", "taskId=" + task.getId() + " appId=" + task.getAppID() + " originalHandler=" + task.getOriginalHandlerID() + " to=" + target.getID());
            ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            target,
                            new TaskOffloadEvent(this.id, target.getIndex(), lti),
                            selectOffloadTargetPid(oi.getNeighbourIndex(), target)
                    );
            this.changedWorkerState = true;
            this.current = null;
        }else{
            // oi.getNeighbourIndex() == this.getId()
            this.tasksToBeLocallyProcessed.add(task.getId());
        }
        return true;
    }



}
