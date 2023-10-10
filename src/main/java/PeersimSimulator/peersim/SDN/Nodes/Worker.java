package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Records.DependentTaskComparator;
import PeersimSimulator.peersim.SDN.Records.LoseTaskInfo;
import PeersimSimulator.peersim.SDN.Tasks.Application;
import PeersimSimulator.peersim.SDN.Tasks.ITask;
import PeersimSimulator.peersim.SDN.Util.Log;
import PeersimSimulator.peersim.SDN.Nodes.Events.*;

import java.util.*;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.transport.Transport;


public class Worker implements CDProtocol, EDProtocol {
    //======================================================================================================
    // Constants/Immutable values
    //======================================================================================================
    private static final String PAR_NAME = "name";
    private static final int RANK_EVENT_DELAY = 5;

    private static final String PAR_MAX_TIME_AFTER_DEADLINE = "maxTimeAfterDeadline";
    private static final int DEFAULT_TIME_AFTER_DEADLINE = 50;

    private final int timeAfterDeadline;



    /**
     * Represents the frequency of the CPUs on this machine.
     */
    public final double CPU_FREQ;
    private static final String PAR_CPU_FREQ = "FREQ";
    public final int CPU_NO_CORES;
    private static final String PAR_CPU_NO_CORES = "NO_CORES";
    public final int Q_MAX;
    private static final String PAR_Q_MAX = "Q_MAX";
    //======================================================================================================
    // Variables
    //======================================================================================================
    /**
     * The node internal id
     */
    private int id;
    /**
     * Protocol identifier, obtained from config property {@link #PAR_NAME}.
     */
    private static int pid;
    /**
     * Number of instructions the CPU can compute in one timestep. This is a gross simplification of the processing power of the CPU.
     * We ignore important factors like the memory management and the influence of the OS.
     */
    public double processingPower;

    /**
     * Tasks dropped on the last cycle.
     */
    public int droppedLastCycle;

    /**
     * Total tasks dropped.
     */
    public int totalDropped;

    public int totalTasksRecieved;
    public int tasksRecievedSinceLastCycle;
    public int totalTasksProcessed;

    public int totalTasksOffloaded;

    /**
     * Queue with the requests assigned to this Node.
     * This represents the Q_i.
     */
    TreeSet<ITask> queue; // TODO Change this to PriorityBlockingQueue

    /**
     * Requests that arrived at this node and are awaiting processing.
     */
    List<Application> recievedApplications;

    Map<String, Application> managedApplications;

    Map<String, LoseTaskInfo> loseTaskInformation;


    ITask current;
    /**
     * Flag for if there have been changes to the queue size o new requests received.
     */
    private boolean changed;


    private Controller correspondingController;

    /**
     * Defines f this node is working as a Worker
     * false - is not; true - is a Worker
     */
    private boolean active;

    // invariant: totalTasksReceived = totalTasksProcessed + totalTasksDropped + totalTasksOffloaded + getQueueSize()


    double minCompLoad;
    double maxCompLoad;

    int minSucc;
    int maxSucc;

    int minArrivalTime;

    double minCompletionRate;
    double maxCompletionRate;

    DependentTaskComparator dependentTaskComparator; // Not sure if this is thread safe tbh...
    boolean hasController;

    /**
     * This variable keeps track of the number of tasks that are in the tasks that require insertion in  the queue.
     */
    int toAddSize;

    public Worker(String prefix) {
        //======== Define the Constant values from Configs ===========//
        // TODO make this definable from the configs.
        // TODO in future have multiple classes of nodes?
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        CPU_FREQ = Configuration.getDouble(prefix + "." + PAR_CPU_FREQ, 1e7);
        CPU_NO_CORES = Configuration.getInt(prefix + "." + PAR_CPU_NO_CORES, 4);
        Q_MAX = Configuration.getInt(prefix + "." + PAR_Q_MAX, 10);
        timeAfterDeadline = Configuration.getInt(prefix+"."+PAR_MAX_TIME_AFTER_DEADLINE, DEFAULT_TIME_AFTER_DEADLINE);
        printParams();
        processingPower = Math.floor(CPU_NO_CORES * CPU_FREQ);
        //======== Init Datastructures ===========//
        hasController = false;
        current = null;
        active = true;
        totalDropped = 0;
        droppedLastCycle = 0;
        totalTasksRecieved = 0;
        tasksRecievedSinceLastCycle = 0;
        totalTasksProcessed = 0;
        totalTasksOffloaded = 0;

        minCompLoad = Double.MAX_VALUE;
        maxCompLoad = Double.MIN_VALUE;

        minSucc = Integer.MAX_VALUE;
        maxSucc = Integer.MIN_VALUE;

        minArrivalTime = Integer.MAX_VALUE;

        minCompletionRate = Double.MAX_VALUE;
        maxCompletionRate = Double.MIN_VALUE;

        toAddSize = 0;

        dependentTaskComparator = new DependentTaskComparator();
        correspondingController = null;
    }

    @Override
    public Object clone() {
        Worker svh = null;
        try {
            svh = (Worker) super.clone();
            svh.resetQueue();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }


    //======================================================================================================
    // Methods
    //======================================================================================================
    @Override
    public void nextCycle(PeersimSimulator.peersim.core.Node node, int protocolID) {
        if (!active) return;
        // Advance Task processing and update status.
        /* Behaviour of the node.
         *  1. A task has a number of instructions. The CPU has 4 cores and a frequency, all CPUs the same frequency.
         *    the amount of task per time step is no_instr / (cores * frequency)
         *  2. Tasks are executed from the beginning of queue by order. If one task is finished, in a timestep all the remaining instructions of that cycle will be used for the next task.
         *  3. If a queue is empty and there are tasks in the recievedRequests those are processed
         *  4. If the there are no tasks to be processed the node idles.
         */
        // droppedLastCycle = 0;
        double remain = processingPower;
        while (remain > 0 && !idle()) {
            if (current == null || current.done()) {
                changed = selectNextTask(node, pid);
                if (current == null) {
                    break;
                }
            }
            remain = current.addProgress(remain);
            if (current.done()) {
                totalTasksProcessed++;

                if (this.id == current.getOriginalHandlerID()) {
                    // Add to application progress
                    Application app = this.managedApplications.get(current.getAppID());
                    app.addProgress(current.getId());
                    if (app.isFinished()) {
                        this.handleApplicationFinish(node, protocolID, app);
                    }

                } else if (this.id != current.getOriginalHandlerID()) { // I just like my ligatures... Leave me alone...
                    int linkableID = FastConfig.getLinkable(protocolID);
                    Linkable linkable = (Linkable) node.getProtocol(linkableID);
                    Node handler = linkable.getNeighbor(current.getOriginalHandlerID());

                    // Clean up the data stru ctures
                    loseTaskInformation.remove(current.getId());

                    if (!handler.isUp()) return; // This happens task progress is lost.
                    Log.info("|WRK| TASK FINISH: SRC<" + this.getId() + "> Task <" + this.current.getId() + ">");
                    ((Transport) handler.getProtocol(FastConfig.getTransport(Client.getPid()))).
                            send(
                                    node,
                                    handler,
                                    new TaskConcludedEvent(this.id, current.getAppID(), current.getClientID(), current.getOutputSizeBytes()),
                                    Client.getPid()
                            );
                }
            }

            if((CommonState.getIntTime() % RANK_EVENT_DELAY) == 0){
                applicationSerialization();
            }

        }

        // Then Communicate changes in Queue Size and Recieved Nodes  to Controller.
        if (this.changed) {
            int linkableID = FastConfig.getLinkable(protocolID);
            Linkable linkable = (Linkable) node.getProtocol(linkableID);
            Node controller = linkable.getNeighbor(0);

            // Quoting the tutorial:
            // XXX quick and dirty handling of failures
            // (message would be lost anyway, we save time)
            if (!controller.isUp()) return;

            // Controller controllerProtocol = (Controller) controller.getProtocol(Controller.getPid());
            Log.info("|WRK| WORKER-INFO SEND: SRC<" + this.getId() + "> Qi<" + this.queue.size() + "> Wi <" + this.recievedApplications.size() + "> Working: <" + !(current == null) + ">");

            ((Transport) controller.getProtocol(FastConfig.getTransport(Controller.getPid()))).
                    send(
                            node,
                            controller,
                            new WorkerInfo(this.id, this.queue.size(), this.recievedApplications.size(), averageTaskSize(), processingPower, Q_MAX - this.getNumberOfTasks() ),
                            Controller.getPid()
                    );
            this.changed = false;
        }


    }

    private void handleApplicationFinish(PeersimSimulator.peersim.core.Node node, int protocolID, Application app) {
        // Send answer to client if app finished!
        // Remove app from local structures
        managedApplications.remove(app.getAppID()); // If this is running then everything has been removed from the application.

        // Send results to client
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        Node client = linkable.getNeighbor(app.getClientID());

        if (!client.isUp()) return; // This happens task progress is lost.
        Log.info("|WRK| TASK FINISH: SRC<" + this.getId() + "> Task <" + this.current.getId() + ">");
        ((Transport) client.getProtocol(FastConfig.getTransport(Client.getPid()))).
                send(
                        node,
                        client,
                        new AppConcludedEvent(this.id, app.getAppID(), app.getOutputDataSize()),
                        Client.getPid()
                );
    }

    private void applicationSerialization() { // God, the complexity of this method offends me. Think of implementing this as rolling values latter on!!
        // Heap listNew = new Heap...
        TreeSet<ITask> newQ = new TreeSet<>(dependentTaskComparator);
        // Compute whatever necessary metrics across all applications -> Store Metrics  in the last metrics variable
        minCompLoad = Double.MAX_VALUE;
        maxCompLoad = Double.MIN_VALUE;

        minSucc = Integer.MAX_VALUE;
        maxSucc = Integer.MIN_VALUE;

        minArrivalTime = Integer.MAX_VALUE;

        minCompletionRate = Double.MAX_VALUE;
        maxCompletionRate = Double.MIN_VALUE;

        Map<String, Double> previousPrioritiesPerTask = new HashMap<>();

        for (Application a : recievedApplications) {
            getDataForPriorityMetrics(a);

        }

        Set<Application> removeApps = new HashSet<>();
        for (ITask t : queue) {

            Application a = managedApplications.get(t.getAppID());
            if(a.getDeadline() + timeAfterDeadline <= CommonState.getIntTime()){
                // TODO
                queue.remove(t);
                removeApps.add(a);
                continue;
            }


            previousPrioritiesPerTask.put(t.getId(), t.getCurrentRank()); // Must have a previous rank to be in the queue.
            if (a != null) {
                getDataForPriorityMetrics(a);
            } else {
                LoseTaskInfo lti = loseTaskInformation.get(t.getId());
                if (lti.getMaxComputation() > maxCompLoad) maxCompLoad = lti.getMaxComputation();
                if (lti.getMinComputation() < minCompLoad) minCompLoad = lti.getMinComputation();

                if (lti.getMaxSuccessors() > maxSucc) maxSucc = lti.getMaxSuccessors();
                if (lti.getMinSuccessors() < minSucc) minSucc = lti.getMinSuccessors();

                if (lti.getArrivalTime() < minArrivalTime) minArrivalTime = lti.getArrivalTime();

                if (lti.getCompletionRate() > maxCompletionRate) maxCompletionRate = lti.getCompletionRate();
                if (lti.getCompletionRate() < minCompletionRate) minCompletionRate = lti.getCompletionRate();
            }
        }

        purgeApps(removeApps);

        // Cycle through queued tasks ranking them and re-inserting them in app
        for (ITask t : queue) {
            double rank;
            if (!isLocal(t))
                rank = remoteTaskRank(loseTaskInformation.get(t.getId()), t, minCompLoad, maxCompLoad, minSucc, maxSucc, minArrivalTime, minCompletionRate, maxCompletionRate);
            else {
                Application app = managedApplications.get(t.getAppID());
                rank = localTaskRank(app, previousPrioritiesPerTask, t, minCompLoad, maxCompLoad, minSucc, maxSucc, minArrivalTime, minCompletionRate, maxCompletionRate);
            }
            t.setCurrentRank(rank);
            newQ.add(t);
        }
        // Cycle through new applications ranking their every-tasks and re-inserting them in app # make shure to cycle through this in a way that all priorities are pre-computed.
        // Note: Add logic to deal with the initial task!!
        for (Application app : managedApplications.values()) {
            List<ITask> tasks = app.expandToList();
            for (ITask t : tasks) {
                double rank = localTaskRank(app, previousPrioritiesPerTask, t, minCompLoad, maxCompLoad, minSucc, maxSucc, minArrivalTime, minCompletionRate, maxCompletionRate);
                t.setCurrentRank(rank);
                newQ.add(t);
            }
        }
        queue = newQ;
        recievedApplications = new LinkedList<>();
        toAddSize = 0;
    }

    private void purgeApps(Set<Application> removeApps) {
        Iterator<Application> iterator = removeApps.iterator();
        while (iterator.hasNext()) {
            String id = iterator.next().getAppID();
            if (managedApplications.containsKey(id)) {
                managedApplications.remove(id);
                iterator.remove(); // Remove the ID from the set as well
            }
        }
    }

    private boolean isLocal(ITask t) {
        return managedApplications.get(t.getAppID()) != null;
    }

    private void getDataForPriorityMetrics(Application a) {
        if (a.getMaxComputation() > maxCompLoad) maxCompLoad = a.getMaxComputation();
        if (a.getMinComputation() < minCompLoad) minCompLoad = a.getMinComputation();

        if (a.getMaxSuccessors() > maxSucc) maxSucc = a.getMaxSuccessors();
        if (a.getMinSuccessors() < minSucc) minSucc = a.getMinSuccessors();

        if (a.getArrivalTime() < minArrivalTime) minArrivalTime = a.getArrivalTime();

        if (a.getCompletionRate() > maxCompletionRate) maxCompletionRate = a.getCompletionRate();
        if (a.getCompletionRate() < minCompletionRate) minCompletionRate = a.getCompletionRate();
    }

    private double localTaskRank(Application app, Map<String, Double> currentPriorities, ITask task, double minCompLoad, double maxCompLoad, int minSucc, int maxSucc, int minArrivalTime, double minCompletionRate, double maxCompletionRate) {
        // Compute Parts of the ranking function TODO make this methods use the fields of the class instead of parameter variables...
        List<ITask> succ = app.getSuccessors().get(task.getId());
        // Normalized Number of Successors#090909
        double En = succ.size();
        double normalized_En = (En - minSucc) / (maxSucc - minSucc);
        // Normalized Computational Load
        double Ln = 1 / task.getTotalInstructions();
        double normalized_Ln = (Ln - minCompLoad) / (maxCompLoad - minCompLoad); // TODO make sure the computational load is 1/L_something
        // Normalized Number of Occurrences (IGNORED)
        // Normalized Urgency Metric
        double urgency = -app.getDeadline() + CommonState.getIntTime() - app.getArrivalTime() + 1;
        double y_normalized = (urgency - minArrivalTime) / (CommonState.getIntTime() - minArrivalTime); // Can trivially keep the latest arrival time aswell btw.
        // Normalized Completion Rate
        double normalized_Completion = (app.getCompletionRate() - minCompletionRate) / (maxCompletionRate - minCompletionRate);
        // Compute the ranking function
        double succPriority = Double.MIN_VALUE;
        for (ITask t : succ) {
            Double priority = currentPriorities.get(t.getId());
            if (priority == null) {
                Log.err("This should never happen, all dependencies should be processed before the task is processed. But if this is logged then it is not happening");
            }
            if (priority > succPriority) {
                succPriority = priority;
            }
        }
        return succPriority + normalized_Completion + normalized_En + normalized_En + y_normalized;
    }

    private double remoteTaskRank(LoseTaskInfo lti, ITask task, double minCompLoad, double maxCompLoad, int minSucc, int maxSucc, int minArrivalTime, double minCompletionRate, double maxCompletionRate) {
        List<String> succ = lti.getSuccessorIDs();
        // TODO I will assume that only tasks that have their dependencies fulfilled are elegible for offloading.
        //  therefore no task that is offloaded will have dependencies on the offloaded node. (aka only tasks in
        //  "parallel/same layer" from one application may be offloaded.

        // Normalized Number of Successors#090909
        double En = succ.size();
        double normalized_En = (En - minSucc) / (maxSucc - minSucc);
        // Normalized Computational Load
        double Ln = 1 / task.getTotalInstructions();
        double normalized_Ln = (Ln - minCompLoad) / (maxCompLoad - minCompLoad); // TODO make sure the computational load is 1/L_something
        // Normalized Number of Occurrences (IGNORED)
        // Normalized Urgency Metric
        double urgency = -lti.getDeadline() + CommonState.getIntTime() - lti.getArrivalTime() + 1;
        double y_normalized = (urgency - minArrivalTime) / (CommonState.getIntTime() - minArrivalTime); // Can trivially keep the latest arrival time aswell btw.
        // Normalized Completion Rate
        double normalized_Completion = (lti.getCompletionRate() - minCompletionRate) / (maxCompletionRate - minCompletionRate);
        // Compute the ranking function
        double succPriority = 0; // I assume that all offloaded nodes have their priorities met, therefore there is no
        // need to check for dependent nodes.

        return succPriority + normalized_Completion + normalized_En + normalized_En + y_normalized;
    }

    /**
     * This method computes the average task size.
     *
     * @return the average number of instructions of the tasks in a node.
     */
    private double averageTaskSize() {
        double acc = this.current == null ? 0 : this.current.getTotalInstructions() - this.current.getProgress();
        double noTasks = this.current == null ? 0 : 1;
        for (ITask t : queue) {
            acc += t.getTotalInstructions();
            noTasks++;
        }
        for (Application t : recievedApplications) {
            acc += t.getTotalTaskSize();
            noTasks++;
        }
        return (noTasks == 0) ? 0 : acc / noTasks; // note: rounds down
    }


    @Override
    public void processEvent(PeersimSimulator.peersim.core.Node node, int pid, Object event) {
        if (!active) return;
        if (event instanceof TaskOffloadEvent ev) { // TODO change this event to send a LoseTaskInfo of the task
            // TODO Add the Task Info of the respective tasks!!!!
            // Receive Tasks Event Receives a set of new Tasks
            // The new tasks are added to the receivedRequests. This
            // is reasonable because this requests can be consumed for
            // processing as well.
            if (this.getId() != ev.getDstNode()) {
                Log.info("|WRK| Offloaded Tasks arrived at wrong node...");
                return;
            }
            ITask offloadedTask = ev.getTask();
            Log.info("|WRK| TASK OFFLOAD RECIEVE: SRC<" + ev.getSrcNode() + "> TARGET<" + this.getId() + ">");

            if (this.getNumberOfTasks() >= Q_MAX) {

                this.droppedLastCycle ++;
                this.totalDropped ++;

                // TODO what is the behaviour of dropping a task? Send the task back to the emitter? Only drop applications?

                Log.err("Dropping Tasks(" + this.droppedLastCycle + ") Node " + this.getId() + " is Overloaded!");
            } else {
                LoseTaskInfo lti =  ev.asLoseTaskInfo();
                this.loseTaskInformation.put(offloadedTask.getId(), lti);
                double rank = remoteTaskRank(lti, offloadedTask, this.minCompLoad, this.maxCompLoad, this.minSucc, this.maxSucc, this.minArrivalTime, this.minCompletionRate, this.maxCompletionRate);
                offloadedTask.setCurrentRank(rank);
                this.queue.add(offloadedTask);

            }

            this.changed = true;

        } else if (event instanceof NewApplicationEvent ev) {

            Log.info("|WRK| NEW APP RECIEVED: ID<" + this.getId() + "> APP_ID<" + ev.getAppID() + ">");
            Application app = ev.getApp();

            this.totalTasksRecieved++;
            this.tasksRecievedSinceLastCycle++;

            if (this.getNumberOfTasks() + app.applicationSize() >= Q_MAX) {
                droppedLastCycle++;
                totalDropped++;
                Log.err("Dropping Application(" + app.getAppID() + "), Node " + this.getId() + " is overloaded!");
                return;
            }

            Log.info("|WRK| NEW APP RECIEVE: ID<" + this.getId() + ">TASK_ID<" + app.getAppID() + ">");
            app.setHandlerID(this.id);
            app.setArrivalTime(CommonState.getIntTime());
            this.managedApplications.put(app.getAppID(), app);
            this.recievedApplications.add(app);
            toAddSize += app.applicationSize();

            this.changed = true;
        }

        // Note: Updates internal state only sends data to user later
    }


    public boolean isActive() {
        return active;
    }

    /**
     * This method computes the amount of tasks currently in the system. Which includes the tasks awaiting processing in
     * the received applications. The tasks currently in the queue and the task being processed right now.
     *
     * @return the total amount of tasks in the node.
     */
    public int getNumberOfTasks() {
        return this.queue.size() + toAddSize + ((current == null || current.done()) ? 0 : 1);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public static int getPid() {
        return pid;
    }

    private boolean idle() {
        return this.queue.isEmpty() && this.recievedApplications.isEmpty(); //  && (this.current == null || this.current.done())
    }

    public double getProcessingPower() {
        return processingPower;
    }

    public void setProcessingPower(double processingPower) {
        this.processingPower = processingPower;
    }

    public boolean isHasController() {
        return hasController;
    }

    public void setHasController(boolean hasController) {
        this.hasController = hasController;
    }

    /**
     * @return The number of dropped tasks since the last time this method was called.
     */
    public int getDroppedLastCycle() {
        int aux_droppedLastCycle = droppedLastCycle;
        droppedLastCycle = 0;
        return aux_droppedLastCycle;
    }

    /**
     * @return all the tasks dropped in this node since the beginning of the simulation.
     */
    public int getTotalDropped() {
        return totalDropped;
    }

    public int getTotalTasksRecieved() {
        return totalTasksRecieved;
    }

    /**
     * @return all the tasks received since the last time this method was called. Independently of how they were handled.
     */
    public int getTasksRecievedSinceLastCycle() {
        int aux_tasksRecievedSinceLastCycle = tasksRecievedSinceLastCycle;
        tasksRecievedSinceLastCycle = 0;
        return aux_tasksRecievedSinceLastCycle;
    }

    public int getTotalTasksProcessed() {
        return totalTasksProcessed;
    }

    public int getTotalTasksOffloaded() {
        return totalTasksOffloaded;
    }

    //======================================================================================================
    // Private Methods
    //======================================================================================================

    /**
     * This method selects the task to be processed next.
     * by order of priority sets current as: the current task if not finished -> the oldest task in queue -> the oldest task in receivedRequests
     * -> null, if there are no tasks in the node.
     */
    public boolean selectNextTask(PeersimSimulator.peersim.core.Node node, int pid) {
        // Check if current task is done, if it isnt return immediatly
        if (current != null && !current.done()) {
            // Finish ongoing task. No changes to current.

            return this.changed;
        }

        // First see if there is any task with all dependencies met in the queue, else Idle and set current null.
        Iterator<ITask> iterTasks = this.queue.descendingIterator();
        boolean waitDecision = false;
        while (iterTasks.hasNext()) {
            ITask t = iterTasks.next();
            if (this.loseTaskInformation.containsKey(t.getId())) {
                current = t;
                iterTasks.remove();
                this.changed = true;
                waitDecision = true;
                break;
            }
            Application app = this.managedApplications.get(t.getId());
            if (app.subTaskCanAdvance(t.getId())) {
                current = t;
                iterTasks.remove();
                this.changed = true;
                waitDecision = true;
                break;
            }
        }
        // If there is stop computation in a spin wait, request the local controller offload decision.
        if (hasController && waitDecision) {
            OffloadInstructions oi = this.correspondingController.requestOffloadInstructions();

            if (oi.getTargetNode() != this.getId()) {
                // Send the task to the node
                int linkableID = FastConfig.getLinkable(pid);
                Linkable linkable = (Linkable) node.getProtocol(linkableID);
                // As everyone knows each othe`r this is should not be problematic.
                if (oi.getTargetNode() < 0 || oi.getTargetNode() > linkable.degree()) {
                    Log.err("|WRK| The requested target node is outside the nodes known by the Worker. ");
                    return false;
                }


                Node target = linkable.getNeighbor(oi.getTargetNode());

                // Quoting the tutorial:
                // XXX quick and dirty handling of failures
                // (message would be lost anyway, we save time)
                if (!target.isUp()) {
                    // Send failed returning tasks.
                    return false;
                }
                LoseTaskInfo lti;

                if(this.managedApplications.get(this.current.getId()) != null){
                    Application app = this.managedApplications.get(this.current.getId());
                    List<String> ids = new LinkedList<>();
                    List<ITask> succs = app.getSuccessors().get(this.current.getId());
                    for (ITask t: succs) {
                        // Not the prettiest TODO improve?
                        ids.add(t.getId());
                    }
                    lti = new LoseTaskInfo(
                            ids,
                            this.current,
                            app.getDeadline(),
                            app.getMinComputation(),
                            app.getMaxComputation(),
                            app.getMinSuccessors(),
                            app.getMaxSuccessors(),
                            app.getArrivalTime(),
                            app.getCompletionRate()
                    );
                }else if(this.loseTaskInformation.containsKey(this.current.getId())){
                    lti = this.loseTaskInformation.remove(this.current.getId());

                }else{
                    /*lti = null;*/
                    Log.err("Something went terribly wrong. A task that should not be in this node is being offloaded. Node: "+ node.getID()+ " Timestep: " +CommonState.getIntTime());
                    return false;
                }

                ((Transport) target.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                        send(
                                node,
                                target,
                                new TaskOffloadEvent(this.id, oi.getTargetNode(), lti),
                                Worker.getPid()
                        );

                this.changed = true;
                // Set current null
                this.current = null;

                // Call this method again
                return selectNextTask(node, pid); // Will make a decision about all tasks that can progress until no
                // more tasks are offloadable or decides to process locally.
            }
            return true;
        }
        return this.changed;
        // If offload decision repeat the process
        // Else change current to task to be processed

        /*if(current != null && !current.done()){
            // Finish ongoing task. No changes to current.

            return this.changed;
        }else if(!queue.isEmpty()){
            // Get next process in the awaiting queue.
            current = queue.poll();

            return true;
        } else if (!recievedApplications.isEmpty()) {
            // When the queue is empty it dips into the undistributed requests.
            current = recievedApplications.remove(0);

            return true;
        }
        // Node has no tasks therefore will idle.
        current = null;*/
    }

    public Controller getCorrespondingController() {
        return correspondingController;
    }

    public void setCorrespondingController(Controller correspondingController) {
        this.correspondingController = correspondingController;
    }

    private void resetQueue() {

        queue = new TreeSet<>(dependentTaskComparator); // Assuming no concurrency within node. If we want to handle multiple requests confirm trx safe.
        loseTaskInformation = new HashMap<>();
        recievedApplications = new LinkedList<>();
        current = null;
    }

    @Override
    public String toString() {
        String curr = (current != null) ? current.getId() : "NULL";
        return "Worker ID<" + this.getId() + "> | Q: " + this.queue.size() + " W: " + this.recievedApplications.size() + " Current: " + curr;
    }

    private void printParams() {
        //if(active)
        Log.dbg("Worker Params: NO_CORES<" + this.CPU_NO_CORES + "> FREQ<" + this.CPU_FREQ + "> Q_MAX<" + this.Q_MAX + ">");
    }
/*else if(event instanceof OffloadInstructions ev){

            // Offload Tasks Event Executes Offloading of data
            int noToOffload;
            if(ev.getNoTasks() <= this.recievedApplications.size())
                noToOffload = ev.getNoTasks();
            else{
                // Guarantee that we don't try to send more tasks than we actually can.
                Log.err("Tried to offload more tasks than are available.");
                // noToOffload = 0;
                 noToOffload = recievedApplications.size();
            }
            int targetNode = ev.getTargetNode();

            List<ITask> moveTasks = new ArrayList<>(noToOffload);
            Log.info("|WRK| TASK OFFLOAD SEND: SRC<"+ this.id + "> TARGET<" + targetNode + "> NO_TASKS<" + noToOffload+ ">");
            int end = this.recievedApplications.size();
            for(int i = 0; i < noToOffload; i++){
                int index = end - 1 - i;
                moveTasks.add(i, this.recievedApplications.remove(index));
            }
            end = this.recievedApplications.size();
            for(int j = 0; j < end; j ++){
                // Add whats left of the received requests to the queue.
                this.queue.add(this.recievedApplications.remove(0));
            }

            totalTasksOffloaded += moveTasks.size();

            // Send list to node
            int linkableID = FastConfig.getLinkable(pid);
            Linkable linkable = (Linkable) node.getProtocol(linkableID);
            // As everyone knows each other this is should not be problematic.
            Node target = linkable.getNeighbor(targetNode);

            // Quoting the tutorial:
            // XXX quick and dirty handling of failures
            // (message would be lost anyway, we save time)
            if(!target.isUp()){
                // Send failed returning tasks.
                this.recievedApplications.addAll(moveTasks);
                return;
            }
            moveTasks = moveTasks.stream().peek((t) -> t.setOriginalHandlerID(targetNode)).toList();
            ((Transport)target.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            target,
                            new TaskOffloadEvent(this.id, targetNode, moveTasks),
                            Worker.getPid()
                    );
            this.changed = true;

        }*/


}
