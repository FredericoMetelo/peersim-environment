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

    /**
     * <code> Map<AppID, Application><code/> to manage and track the tasks that where offloaded to this node.
     */
    Map<String, Application> managedApplications;

    /**
     * <code> Map<\TaskID, Information about task\><code/> to manage and track the tasks that where offloaded to this node.
     */
    Map<String, LoseTaskInfo> loseTaskInformation;

    /**
     * Task being currently processed. If current is null then the worker is idling
     */
    ITask current;
    /**
     * Flag for if there have been changes to the queue size, a new task is being processed or new applications where
     * received.
     */
    private boolean changedWorkerState;


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

    double minArrivalTime;

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
        timeAfterDeadline = Configuration.getInt(prefix + "." + PAR_MAX_TIME_AFTER_DEADLINE, DEFAULT_TIME_AFTER_DEADLINE);
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
        /* Behaviour of this method:
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
        if (!active) return;
        // Advance Task processing and update status.
        double remainingProcessingPower = processingPower;
        while (remainingProcessingPower > 0 && !this.idle()) {
            if (this.current == null || this.current.done()) {
                changedWorkerState = chooseTaskProcessingStrategy(node, pid);
                if (current == null) {
                    break;
                }
            }
            remainingProcessingPower = current.addProgress(remainingProcessingPower);
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

                    // Clean up the data structures
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
            if ((CommonState.getTime() % RANK_EVENT_DELAY) == 0 && !this.recievedApplications.isEmpty()) {
                applicationSerialization();
            }
        }

        // Then Communicate changes in Queue Size and Recieved Nodes  to Controller.
        if (this.changedWorkerState) {
            int linkableID = FastConfig.getLinkable(protocolID);
            Linkable linkable = (Linkable) node.getProtocol(linkableID);
            Node controller = linkable.getNeighbor(0);
            if (!controller.isUp()) return;
            Log.info("|WRK| WORKER-INFO SEND: SRC<" + this.getId() + "> Qi<" + this.queue.size() + "> Wi <" + this.recievedApplications.size() + "> Working: <" + !(current == null) + ">");
            ((Transport) controller.getProtocol(FastConfig.getTransport(Controller.getPid()))).
                    send(
                            node,
                            controller,
                            new WorkerInfo(this.id, this.queue.size(), this.recievedApplications.size(), averageTaskSize(), processingPower, Q_MAX - this.getNumberOfTasks()),
                            Controller.getPid()
                    );
            this.changedWorkerState = false;
        }


    }

    @Override
    public void processEvent(PeersimSimulator.peersim.core.Node node, int pid, Object event) {
        if (!active) return;
        if (event instanceof TaskOffloadEvent ev) {
            handleTaskOffloadEvent(ev);
        } else if (event instanceof NewApplicationEvent ev) {
            handleNewApplicationEvent(ev);
        }

        // Note: Updates internal state only sends data to user later
    }



    //======================================================================================================
    // Private Methods
    //======================================================================================================

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

    private void handleTaskOffloadEvent(TaskOffloadEvent ev) {
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

            this.droppedLastCycle++;
            this.totalDropped++;

            // TODO what is the behaviour of dropping a task? Send the task back to the emitter? Only drop applications?

            Log.err("Dropping Tasks(" + this.droppedLastCycle + ") Node " + this.getId() + " is Overloaded!");
        } else {
            LoseTaskInfo lti = ev.asLoseTaskInfo();
            this.loseTaskInformation.put(offloadedTask.getId(), lti);
            double rank = remoteTaskRank(lti, offloadedTask);
            offloadedTask.setCurrentRank(rank);
            this.queue.add(offloadedTask);

        }

        this.changedWorkerState = true;
    }

    private void handleNewApplicationEvent(NewApplicationEvent ev) {
        Application app = ev.getApp();

        this.totalTasksRecieved++;
        this.tasksRecievedSinceLastCycle++;

        if (this.getNumberOfTasks() + app.applicationSize() >= Q_MAX) {
            droppedLastCycle++;
            totalDropped++;
            Log.err("Dropping Application(" + app.getAppID() + "), Node " + this.getId() + " is overloaded!");
            return;
        }

        Log.info("|WRK| NEW APP RECIEVED: ID<" + this.getId() + "> APP_ID<" + ev.getAppID() + ">");
        //Log.info("|WRK| NEW APP RECIEVE: ID<" + this.getId() + ">TASK_ID<" + app.getAppID() + ">");
        app.setHandlerID(this.id);
        app.setArrivalTime(CommonState.getTime());
        this.managedApplications.put(app.getAppID(), app);
        this.recievedApplications.add(app);
        toAddSize += app.applicationSize();

        this.changedWorkerState = true;
    }

    /**
     * Ranks all tasks in the node and sort's them in a queue (except the one being currently processed).
     * If any applications have passed their deadline plus a grace period, this applications are removed and aren't
     * added to the queue.
     */
    private void applicationSerialization() {

        TreeSet<ITask> newQ = new TreeSet<>(dependentTaskComparator);

        minCompLoad = Double.MAX_VALUE;
        maxCompLoad = Double.MIN_VALUE;
        minSucc = Integer.MAX_VALUE;
        maxSucc = Integer.MIN_VALUE;
        minArrivalTime = Integer.MAX_VALUE;
        minCompletionRate = Double.MAX_VALUE;
        maxCompletionRate = Double.MIN_VALUE;

        Map<String, Double> previousPrioritiesPerTask = new HashMap<>();
        if (current != null) previousPrioritiesPerTask.put(current.getId(), current.getCurrentRank());
        for (Application a : recievedApplications) {
            getDataForPriorityMetrics(a);
        }

        Set<Application> removeApps = new HashSet<>();
        for (ITask t : queue) {

            Application a = managedApplications.get(t.getAppID());
            if (a.getDeadline() + timeAfterDeadline <= CommonState.getTime()) {
                queue.remove(t);
                removeApps.add(a);
                continue;
            }

            previousPrioritiesPerTask.put(t.getId(), t.getCurrentRank()); // Must have a previous rank to be in the queue.
            if (a != null) {
                getDataForPriorityMetrics(a);
            } else {
                LoseTaskInfo lti = loseTaskInformation.get(t.getId());
                getDataForPriorityMetrics(lti);
            }
        }

        purgeApps(removeApps);

        // Cycle through queued tasks ranking them and re-inserting them in app
        for (ITask t : queue) {
            double rank;
            if (!isTaskLocal(t))
                rank = remoteTaskRank(loseTaskInformation.get(t.getId()), t);
            else {
                Application app = managedApplications.get(t.getAppID());
                rank = localTaskRank(app, previousPrioritiesPerTask, t);
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
        // Normalized Computational Load
        // Normalized Number of Occurrences (IGNORED)
        // Normalized Urgency Metric
        // Normalized Completion Rate
        // Compute the ranking function
        List<ITask> succ = app.getSuccessors().get(task.getId());
        double En = succ.size();
        double normalized_En = (En) / (this.maxSucc);
        double Ln = 1 / task.getTotalInstructions();
        double normalized_Ln = (Ln - 1 / this.minCompLoad) / (1 / this.maxCompLoad - 1 / this.minCompLoad); // TODO make sure the computational load is 1/L_something
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
                    Log.err("This should never happen, all dependencies should be processed before the task is processed. But if this is logged then it is not happening");
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
        double normalized_En = (En) / (this.maxSucc);
        double Ln = 1 / task.getTotalInstructions();
        double normalized_Ln = (Ln - 1 / this.minCompLoad) / (1 / this.maxCompLoad - 1 / this.minCompLoad);
        double urgency = -lti.getDeadline() + CommonState.getTime() - lti.getArrivalTime() + 1;
        double y_normalized = (urgency - this.minArrivalTime + 1) / (CommonState.getTime() - this.minArrivalTime + 1);
        double normalized_Completion = (lti.getCompletionRate() - this.minCompletionRate) / (this.maxCompletionRate - this.minCompletionRate);
        double succPriority = 0; // I assume that all offloaded nodes have their priorities met, therefore there is no need to check for dependent nodes.

        return succPriority + normalized_Completion + normalized_En + normalized_En + y_normalized + normalized_Ln;
    }

    private boolean isTaskLocal(ITask t) {
        return managedApplications.get(t.getAppID()) != null;
    }


    /**
     * This method selects the task to be processed next. Whenever a task is selected computation halts and awaits for
     * the decision whether to offload or not. This method will make the decision whether to offload or not for every
     * available task by recursively calling itself. If all the processable tasks in the queue are offloaded, this method
     * sets current null. The recursion halts if a task is picked to be processed locally.
     * TODO Better name? This method is responsible for selecting a task and choosing what happens to said task (offlaod or process locally)
     * @return Whether <code>this.current</code> changed value || if <code>changed <code/> is <code>true</code>
     */
    public boolean chooseTaskProcessingStrategy(PeersimSimulator.peersim.core.Node node, int pid) {
        // Check if current task is done, if it isn't return immediately
        if (current != null && !current.done()) {
            return this.changedWorkerState;
        }

        // If the node is done with all it's work (current and queue) and there are applications awaiting serialization. Then an early serialization is done.
        // Regularly Scheduled serializations will happen all the same.
        if ((current == null || current.done()) && queue.isEmpty() && !recievedApplications.isEmpty()) {
            applicationSerialization();
        }

        // Go through the queue, and put the first task with dependencies met in this.current. If no task has
        // dependencies met does not change current.
        boolean waitDecisionRequired = selectNextTaskWithDependenciesMet();

        // If a new task was selected decides whether to offload or process locally.
        if (hasController && waitDecisionRequired) {
            // Halts the simulation and awaits the offloading instructions from outside.
            OffloadInstructions oi = this.correspondingController.requestOffloadInstructions();
            if (oi.getTargetNode() != this.getId()) {
                int linkableID = FastConfig.getLinkable(pid);
                Linkable linkable = (Linkable) node.getProtocol(linkableID);
                Node target = linkable.getNeighbor(oi.getTargetNode());
                if (oi.getTargetNode() < 0 || oi.getTargetNode() > linkable.degree() || !target.isUp()) {
                    Log.err("|WRK| The requested target node is outside the nodes known by the Worker="
                            + (oi.getTargetNode() < 0 || oi.getTargetNode() > linkable.degree())
                            + ". Or is down=" + target.isUp());
                    return false;
                }

                LoseTaskInfo lti = getOrGenerateLoseTaskInfo(node);
                if (lti == null) throw new RuntimeException("Task that should not be in the node is being offloaded");
                ((Transport) target.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                        send(
                                node,
                                target,
                                new TaskOffloadEvent(this.id, oi.getTargetNode(), lti),
                                Worker.getPid()
                        );
                this.changedWorkerState = true;
                this.current = null;
                return chooseTaskProcessingStrategy(node, pid);

            }
            return true;
        }
        return this.changedWorkerState;
    }

    /**
     * First see if there is any task with all dependencies met in the queue, else Idle and set current null.
     * @return <code>true</code> if a task was selected to current, <code>false</code> if the node is idling.
     */
    private boolean selectNextTaskWithDependenciesMet() {
        Iterator<ITask> iterOverQueuedTasks = this.queue.descendingIterator();
        boolean waitDecisionRequired = false;
        while (iterOverQueuedTasks.hasNext()) {
            ITask t = iterOverQueuedTasks.next();
            if (this.loseTaskInformation.containsKey(t.getId())) {
                this. current = t;
                iterOverQueuedTasks.remove();
                this.changedWorkerState = true;
                waitDecisionRequired = true;
                break;
            }
            Application app = this.managedApplications.get(t.getAppID());
            if (app != null && app.subTaskCanAdvance(t.getId())) {
                this.current = t;
                iterOverQueuedTasks.remove();
                this.changedWorkerState = true;
                waitDecisionRequired = true;
                break;
            }
        }
        return waitDecisionRequired;
    }

    private LoseTaskInfo getOrGenerateLoseTaskInfo(Node node) {
        LoseTaskInfo lti;
        if (this.managedApplications.get(this.current.getAppID()) != null) {
            Application app = this.managedApplications.get(this.current.getAppID());
            List<String> ids = new LinkedList<>();
            List<ITask> succs = app.getSuccessors().get(this.current.getId());
            for (ITask t : succs) {
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
        } else if (this.loseTaskInformation.containsKey(this.current.getId())) {
            lti = this.loseTaskInformation.remove(this.current.getId());
        } else {
            Log.err("Something went terribly wrong. A task that should not be in this node is being offloaded. Node: " + node.getID() + " Timestep: " + CommonState.getIntTime());
            return null;
        }
        return lti;
    }

    private void resetQueue() {
        queue = new TreeSet<>(dependentTaskComparator); // Assuming no concurrency within node. If we want to handle multiple requests confirm trx safe.
        loseTaskInformation = new HashMap<>();
        recievedApplications = new LinkedList<>();
        managedApplications = new HashMap<>();
        current = null;
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

    private void printParams() {
        //if(active)
        Log.dbg("Worker Params: NO_CORES<" + this.CPU_NO_CORES + "> FREQ<" + this.CPU_FREQ + "> Q_MAX<" + this.Q_MAX + ">");
    }


    //======================================================================================================
    // Getter and Setter Methods
    //======================================================================================================
    @Override
    public String toString() {
        String curr = (current != null) ? current.getId() : "NULL";
        return "Worker ID<" + this.getId() + "> | Q: " + this.queue.size() + " W: " + this.recievedApplications.size() + " Current: " + curr;
    }

    private boolean idle() {
        return this.queue.isEmpty() && this.recievedApplications.isEmpty() && this.current == null; //  && (this.current == null || this.current.done())
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

    public boolean isActive() {
        return active;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static int getPid() {
        return pid;
    }

    public void setProcessingPower(double processingPower) {
        this.processingPower = processingPower;
    }

    public double getProcessingPower() {
        return processingPower;
    }

    public void setCorrespondingController(Controller correspondingController) {
        this.correspondingController = correspondingController;
    }

    public void setHasController(boolean hasController) {
        this.hasController = hasController;
    }

    public boolean isHasController() {
        return hasController;
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
}
