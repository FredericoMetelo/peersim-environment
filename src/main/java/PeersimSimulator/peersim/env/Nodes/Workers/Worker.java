package PeersimSimulator.peersim.env.Nodes.Workers;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Clients.Client;
import PeersimSimulator.peersim.env.Nodes.Cloud;
import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Records.DependentTaskComparator;
import PeersimSimulator.peersim.env.Records.LoseTaskInfo;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.TaskHistory;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.env.Nodes.Events.*;

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
    private static final int RANK_EVENT_DELAY = 3;

    private static final String PAR_MAX_TIME_AFTER_DEADLINE = "maxTimeAfterDeadline";
    private static final int DEFAULT_TIME_AFTER_DEADLINE = 5;
    public static final String EVENT_WORKER_INFO_SEND = "WRK-INFO BROADCAST";
    public static final String EVENT_TASK_FINISH = "TASK FINISH";
    public static final String EVENT_APP_FINISH = "APP FINISH";
    public static final String EVENT_OFFLOADED_TASKS_ARRIVED_AT_WRONG_NODE = "Offloaded Tasks Arrived at Wrong Node";
    public static final String EVENT_TASK_OFFLOAD_RECIEVE = "TASK OFFLOAD RECIEVE";
    public static final String EVENT_OVERLOADED_NODE = "OVERLOADED NODE";
    public static final String EVENT_NEW_APP_RECIEVED = "NEW APP RECIEVED";
    public static final String EVENT_ERR_NODE_OUT_OF_BOUNDS = "NODE OUT OF BOUNDS";
    private static final String EVENT_WORKER_INFO_SEND_FAILED = "WRK-INFO SEND FAIL";
    public static final String EVENT_NO_TASK_PROCESS = "NO TASK FOR PROCESS";
    public static final String EVENT_NO_TASK_OFFLOAD = "NO TASK FOR OFFLOAD";
    private static final String EVENT_ERR_NO_TARGET_PID_AVAILABLE = "NO TARGET PID ACTIVE";


    private final int timeAfterDeadline;


    /**
     * Represents the frequency of the CPUs on this machine.
     */
    public double cpuFreq;
    public int cpuNoCores;
    public int qMAX;
    public int layer;
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

    Set<String> tasksToBeLocallyProcessed;

    int tasksWithAllDepenciesMet;

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


    private List<ITask> tasksCompletedSinceLastCycle;

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
    private SDNNodeProperties props;

    public Worker(String prefix) {
        //======== Define the Constant values from Configs ===========//
        // TODO make this definable from the configs.
        // TODO in future have multiple classes of nodes?
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        timeAfterDeadline = Configuration.getInt(prefix + "." + PAR_MAX_TIME_AFTER_DEADLINE, DEFAULT_TIME_AFTER_DEADLINE);
        printParams();
        //======== Init Datastructures ===========//
        hasController = false;
        current = null;
        active = false;
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

    public void workerInit(double cpuFreq, int noCores, int qMax, int layer){
        this.processingPower = Math.floor(noCores * cpuFreq);
        this.cpuFreq = cpuFreq;
        this.cpuNoCores = noCores;
        this.qMAX = qMax;
        this.layer = layer;
        this.active = true;
        // TODO Was currently testing the behaviour of the worker activation. Need to gurarantee that
        //  the cloud does not initialize as a Worker.
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
    public void nextCycle(PeersimSimulator.peersim.core.Node node, int protocolID) {
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

        // Then Communicate changes in Queue Size and Recieved Nodes  to Controller.
        if (this.changedWorkerState) { // TODO Guarantee we inform neighbours. Guarantee no double offloading.
            broadcastStateChanges(node, protocolID);
        }
    }

    private boolean awaitingSerialization() {
        return this.queue.isEmpty() && !this.recievedApplications.isEmpty();
    }

    @Override
    public void processEvent(PeersimSimulator.peersim.core.Node node, int pid, Object event) {
        if (!active) return;
        if (event instanceof TaskOffloadEvent ev) {
            handleTaskOffloadEvent(ev);
        } else if (event instanceof NewApplicationEvent ev) {
            handleNewApplicationEvent(ev); // TODO this is considered overloaded when it shouldn't be overloaded.
        }else if(event instanceof TaskConcludedEvent ev){
            ITask finishedTask = ev.getTask();
            handleTaskConcludedEvent(node, pid, finishedTask);
        }

        // Note: Updates internal state only sends data to user later
    }




    //======================================================================================================
    // Private Methods
    //======================================================================================================
    private void broadcastStateChanges(Node node, int protocolID) {
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        wrkInfoLog(EVENT_WORKER_INFO_SEND, "Q_size=" + this.queue.size() + " rcv_Apps=" + this.recievedApplications.size() + " working=" + !(current == null));
        for (int i = 0; i < linkable.degree(); i++) {
            Node controller = linkable.getNeighbor(i);
            if (!controller.isUp()) {
                wrkInfoLog(EVENT_WORKER_INFO_SEND_FAILED, "target="+controller.getID());
                continue;
            }
            ((Transport) node.getProtocol(FastConfig.getTransport(Controller.getPid()))).
                    send(
                            node,
                            controller,
                            compileWorkerInfo(),
                            Controller.getPid()
                    );
        }

        this.changedWorkerState = false;
    }

    private void handleRemoteTaskFinish(Node node, int protocolID, ITask task) {
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        Node handler = this.getNodeFromId(task.pollLastConnectionId(), linkable);

        // Clean up the data structures
        loseTaskInformation.remove(task.getId());

        if (handler == null || !handler.isUp()) {
            wrkErrLog("NO CTR FOR REMOTE TSK", "Node does not know Node="+task.getOriginalHandlerID() +" that requested task=" +task.getId()+", dropping task" );
        }else {
            wrkInfoLog(EVENT_TASK_FINISH,  "taskId=" + task.getId());
            ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            handler,
                            new TaskConcludedEvent(this.id, task.getAppID(), task.getClientID(), task.getOutputSizeBytes(), task),
                            Worker.getPid()
                    );
        }
    }

    private void handleTaskFinish(Node node, int protocolID, ITask finishedTask) {
        Application app = this.managedApplications.get(finishedTask.getAppID());
        app.addProgress(finishedTask.getId());
        finishedTask.addEvent(TaskHistory.TaskEvenType.COMPLETED, this.id, CommonState.getTime());
        tasksCompletedSinceLastCycle.add(finishedTask);
        if (app.isFinished()) {
            this.handleApplicationFinish(node, protocolID, app);
        }
    }

    private void handleTaskConcludedEvent(Node node, int protocolID, ITask finishedTask) {
        if(finishedTask.getOriginalHandlerID()  != this.id){
            handleRemoteTaskFinish(node, protocolID, finishedTask);
        }else{
            handleTaskFinish(node, protocolID, finishedTask);
        }
    }

    private void handleApplicationFinish(PeersimSimulator.peersim.core.Node node, int protocolID, Application app) {
        // Send answer to client if app finished!
        // Remove app from local structures
        managedApplications.remove(app.getAppID()); // If this is running then everything has been removed from the application.

        // Send results to client
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        Node client = this.getNodeFromId(app.getClientID(), linkable);

        if (!client.isUp()) return; // This happens task progress is lost.
        wrkInfoLog(EVENT_APP_FINISH, "appId=" + app.getAppID() + " deadlineWas="+app.getDeadline() + " finished=" + app.isFinished());
        ((Transport) node.getProtocol(FastConfig.getTransport(Client.getPid()))).
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
            wrkErrLog(EVENT_OFFLOADED_TASKS_ARRIVED_AT_WRONG_NODE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID() +" arrivedAt=" + this.getId() + " supposedToArriveAt=" + ev.getDstNode());
            return;
        }
        ITask offloadedTask = ev.getTask();
        offloadedTask.addEvent(TaskHistory.TaskEvenType.OFFLOADED, this.id, CommonState.getTime());
        wrkInfoLog(EVENT_TASK_OFFLOAD_RECIEVE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID());
        if (this.getNumberOfTasks() > qMAX) {
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

    private void handleNewApplicationEvent(NewApplicationEvent ev) {
        Application app = ev.getApp();

        this.totalTasksRecieved++;
        this.tasksRecievedSinceLastCycle++;

        if (this.getNumberOfTasks() + app.applicationSize() > qMAX) {
            droppedLastCycle++;
            totalDropped++;
            wrkInfoLog(EVENT_OVERLOADED_NODE, " DroppedApp="+app.getAppID());
            return;
        }

        wrkInfoLog(EVENT_NEW_APP_RECIEVED, " appId="+ev.getAppID());
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
     *
     */
    private void applicationSerialization() {

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
        recievedApplications = new LinkedList<>();
        toAddSize = 0;
    }


    /**
     * This method collects all the expired apps and removes them from the simulation.
     */
    private void cleanExpiredApps(){
        Iterator<ITask> qiterator = queue.iterator();
        Set<Application> removeApps = new HashSet<>();
        while (qiterator.hasNext()) {
            ITask t = qiterator.next();
            Application a = managedApplications.get(t.getAppID());
            if (a != null && a.getDeadline() + timeAfterDeadline <= CommonState.getTime()) {
                t.addEvent(TaskHistory.TaskEvenType.DROPPED, this.id, CommonState.getTime());
                tasksCompletedSinceLastCycle.add(t);
                qiterator.remove();
                removeApps.add(a);
                continue;
            }
            LoseTaskInfo l = loseTaskInformation.get(t.getId()); // Probably do not need to fetch taskinfo twice... Laughs in O(1)
            if (l != null && l.getDeadline() + timeAfterDeadline <= CommonState.getTime()) {
                loseTaskInformation.remove(t.getId());
                qiterator.remove();
                if (current != null && Objects.equals(current.getId(), t.getId())) this.current = null;
                continue;
            }
        }
        purgeApps(removeApps);
    }

    /**
     * This method remocves all apps in parameter list of apps removeApps from the simulation
     * @param removeApps
     */
    private void purgeApps(Set<Application> removeApps) {
        Iterator<Application> iterator = removeApps.iterator();
        while (iterator.hasNext()) {
            Application app = iterator.next();
            String id = app.getAppID();
            if (managedApplications.containsKey(id)) {
                managedApplications.remove(id);
                Log.info("Cleaning Application("+id+"), Node "+this.getId()+"deadline(" + app.getDeadline() + ") expired. Curr Time:"+CommonState.getTime());
                if(current != null && Objects.equals(current.getAppID(), id)) this.current = null;
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

    private boolean isTaskLocal(ITask t) {
        return managedApplications.get(t.getAppID()) != null;
    }

    private boolean validOffloadingInstructions(OffloadInstructions oi, Linkable linkable){
        return oi.getNeighbourIndex() > 0 || oi.getNeighbourIndex() < linkable.degree();
    }

    public WorkerInfo compileWorkerInfo(){
        return new WorkerInfo(this.id, this.queue.size(), this.unprocessedTasksInApps() , averageTaskSize(), processingPower, qMAX - this.getNumberOfTasks(), this.getLayer(), this.getProps().getCoordinates());

    }

    /**
     * This method selects the task to be processed next. Whenever a task is selected computation halts and awaits for
     * the decision whether to offload or not. This method will make the decision whether to offload or not for every
     * available task by recursively calling itself. If all the processable tasks in the queue are offloaded, this method
     * sets current null. The recursion halts if a task is picked to be processed locally.
     * @return Whether <code>this.current</code> changed value || if <code>changed <code/> is <code>true</code>
     */
    public boolean nextProcessableTask(PeersimSimulator.peersim.core.Node node, int pid) {
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

    public boolean offloadInstructions(int pid, OffloadInstructions oi) {
        if(this.awaitingSerialization()) applicationSerialization();

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
            if(!validOffloadingInstructions(oi, linkable)) {
                return false;
            }

            Node target = linkable.getNeighbor(oi.getNeighbourIndex());
            if ( !target.isUp()) {
                wrkErrLog(EVENT_ERR_NODE_OUT_OF_BOUNDS, "The requested target node is outside the nodes known by the Worker="
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

    private int selectOffloadTargetPid(int targetIndex, Node target){
        int pid;
        if(((Worker) target.getProtocol(Worker.getPid())).isActive()){
            pid = Worker.getPid();
        }else if(((Cloud) target.getProtocol(Cloud.getPid())).isActive()){
            pid = Cloud.getPid();
        }else{
            wrkErrLog(EVENT_ERR_NO_TARGET_PID_AVAILABLE,"The target node is not a Worker nor a Cloud. This should not happen. Killing the simulation.");
            throw new RuntimeException("The target node is not a Worker nor a Cloud. This should not happen. Killing the simulation.");
        }
        return pid;
    }

    private LoseTaskInfo getOrGenerateLoseTaskInfo(Node node, ITask task) {
        LoseTaskInfo lti;
        if (this.managedApplications.get(task.getAppID()) != null) {
            Application app = this.managedApplications.get(task.getAppID());
            List<String> ids = new LinkedList<>();
            List<ITask> succs = app.getSuccessors().get(task.getId());
            for (ITask t : succs) {
                // Not the prettiest TODO improve?
                ids.add(t.getId());
            }
            lti = new LoseTaskInfo(
                    ids,
                    task,
                    app.getDeadline(),
                    app.getMinComputation(),
                    app.getMaxComputation(),
                    app.getMinSuccessors(),
                    app.getMaxSuccessors(),
                    app.getArrivalTime(),
                    app.getCompletionRate()
            );
        } else if (this.loseTaskInformation.containsKey(task.getId())) {
            lti = this.loseTaskInformation.remove(task.getId());
        } else {
            wrkErrLog("ERROR","Something went terribly wrong. A task that should not be in this node is being offloaded. Node: " + node.getID() + " Timestep: " + CommonState.getIntTime());
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

        tasksCompletedSinceLastCycle = new LinkedList<>();
        tasksToBeLocallyProcessed = new HashSet<>();
        tasksWithAllDepenciesMet = 0;
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
        wrkDbgLog("Worker Params: NO_CORES<" + this.cpuNoCores + "> FREQ<" + this.cpuFreq + "> Q_MAX<" + this.qMAX + ">");
    }


    //======================================================================================================
    // Getter and Setter Methods
    //======================================================================================================
    @Override
    public String toString() {
        String curr = (current != null) ? current.getId() : "NULL";
        return (this.active) ? "Worker ID<" + this.getId() + "> | Q: " + this.queue.size() + " W: " + this.recievedApplications.size() + " Current: " + curr
                : "Worker <INACTIVE>";
    }

    /**
     * Looks for the node with a given ID in the known nodes, stored in this node's {@link Linkable} object. If the node
     * does not exist returns <code>null</code>
     * @param id, id of the node to be looked for
     * @param linkable, the {@link Linkable} object of this node
     * @return the node with the id or null if there is no node with the given id
     */
    private Node getNodeFromId(int id, Linkable linkable){
        for (int i = 0; i < linkable.degree(); i++) {
           Node n = linkable.getNeighbor(i);
           if(n.getID() == id) return n;
        }
        return null;
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


    public int unprocessedTasksInApps(){
        return this.recievedApplications.stream().map(Application::applicationSize).reduce(0, Integer::sum);
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
    public SDNNodeProperties getProps() {
        return props;
    }

    public void setProps(SDNNodeProperties props) {
        this.props = props;
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

    public int getLayer() {
        return layer;
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


    public void wrkInfoLog(String event, String info){
        Log.logInfo("WRK", this.id, event, info);

    }
    public List<ITask> extractCompletedTasks() {
        List<ITask> aux = tasksCompletedSinceLastCycle;
        tasksCompletedSinceLastCycle = new LinkedList<>();
        return aux;
    }
    public void wrkDbgLog(String msg){
        Log.logDbg("WRK", this.id, "DEBUG", msg);
    }

    public void wrkErrLog(String event, String msg){
        Log.logErr("WRK", this.id, event, msg);
    }


    public int getCpuNoCores() {
        return cpuNoCores;
    }

    public double getAverageWaitingTime() {
        // TODO add the part corresponding to the waiting time of the tasks in the managed app.
        double managedAvgWaitTime = this.recievedApplications.stream().mapToDouble(Application::getTotalTaskSize).average().orElse(0);
        double queueAvgWaitTime = this.queue.stream().mapToDouble(ITask::getTotalInstructions).average().orElse(0);
        return (managedAvgWaitTime +queueAvgWaitTime ) / processingPower;
    }
}
