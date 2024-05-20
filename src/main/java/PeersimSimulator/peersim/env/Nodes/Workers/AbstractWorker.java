package PeersimSimulator.peersim.env.Nodes.Workers;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Clients.Client;
import PeersimSimulator.peersim.env.Nodes.Cloud;
import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Nodes.Events.*;
import PeersimSimulator.peersim.env.Records.DependentTaskComparator;
import PeersimSimulator.peersim.env.Records.LoseTaskInfo;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.TaskHistory;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.transport.Transport;

import java.util.*;

public abstract class AbstractWorker implements Worker {
    /**
     * Protocol identifier, obtained from config property {@link #PAR_NAME}.
     */
    protected static int pid;
    protected final int timeAfterDeadline;
    /**
     * Represents the frequency of the CPUs on this machine.
     */
    public double cpuFreq;
    public int cpuNoCores;
    public int qMAX;
    public int layer;
    /**
     * The node internal id
     */
    protected int id;
    /**
     * Number of instructions the CPU can compute in one timestep. This is a gross simplification of the processing power of the CPU.
     * We ignore important factors like the memory management and the influence of the OS.
     */
    protected double processingPower;
    /**
     * Tasks dropped on the last cycle.
     */
    protected int droppedLastCycle;
    /**
     * Total tasks dropped.
     */
    protected int totalDropped;
    protected int totalTasksRecieved;
    protected int tasksRecievedSinceLastCycle;
    protected int totalTasksProcessed;
    protected int totalTasksOffloaded;
    /**
     * Queue with the requests assigned to this Node.
     * This represents the Q_i.
     */
    protected TreeSet<ITask> queue; // TODO Change this to PriorityBlockingQueue
    /**
     * Requests that arrived at this node and are awaiting processing.
     */
    protected List<Application> recievedApplications;
    /**
     * <code> Map<AppID, Application><code/> to manage and track the tasks that where offloaded to this node.
     */
    protected Map<String, Application> managedApplications;
    /**
     * <code> Map<\TaskID, Information about task\><code/> to manage and track the tasks that where offloaded to this node.
     */
    protected Map<String, LoseTaskInfo> loseTaskInformation;
    protected Set<String> tasksToBeLocallyProcessed;
    /**
     * Task being currently processed. If current is null then the worker is idling
     */
    protected ITask current;
    /**
     * Flag for if there have been changes to the queue size, a new task is being processed or new applications where
     * received.
     */
    protected boolean changedWorkerState;
    protected Controller correspondingController;
    /**
     * Defines f this node is working as a DAGWorker
     * false - is not; true - is a DAGWorker
     */
    protected boolean active;
    protected List<ITask> tasksCompletedSinceLastCycle;
    protected DependentTaskComparator dependentTaskComparator; // Not sure if this is thread safe tbh...
    protected boolean hasController;
    /**
     * This variable keeps track of the number of tasks that are in the tasks that require insertion in  the queue.
     */
    protected int toAddSize;
    protected SDNNodeProperties props;

    protected double energyConsumed;
    protected double costOfCommPerByte;
    protected double costOfProcessPerByte;


    public AbstractWorker(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        droppedLastCycle = 0;
        totalDropped = 0;
        totalTasksRecieved = 0;
        tasksRecievedSinceLastCycle = 0;
        totalTasksProcessed = 0;
        totalTasksOffloaded = 0;
        current = null;
        correspondingController = null;
        active = false;
        dependentTaskComparator = new DependentTaskComparator();
        hasController = false;
        toAddSize = 0;
        energyConsumed = 0;
        timeAfterDeadline = Configuration.getInt(prefix + "." + PAR_MAX_TIME_AFTER_DEADLINE, DEFAULT_TIME_AFTER_DEADLINE);
        costOfCommPerByte = Configuration.getDouble(prefix + "." + PAR_ENERGY_COST_COMM);
        costOfProcessPerByte = Configuration.getDouble(prefix + "." + PAR_ENERGY_COST_COMP);
    }

    @Override
    public Object clone() {
        AbstractWorker svh = null;
        try {
            svh = (AbstractWorker) super.clone();
            svh.resetDataStructures();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }

    @Override
    public void workerInit(double cpuFreq, int noCores, int qMax, int layer) {
        this.processingPower = Math.floor(noCores * cpuFreq);
        this.cpuFreq = cpuFreq;
        this.cpuNoCores = noCores;
        this.qMAX = qMax;
        this.layer = layer;
        this.active = true;
        // TODO Was currently testing the behaviour of the worker activation. Need to gurarantee that
        //  the cloud does not initialize as a DAGWorker.
    }

    @Override
    public void processEvent(PeersimSimulator.peersim.core.Node node, int pid, Object event) {
        if (!active) return;
        if (event instanceof TaskOffloadEvent ev) {
            handleTaskOffloadEvent(ev);
        } else if (event instanceof NewApplicationEvent ev) {
             handleNewApplicationEvent(ev); // TODO this is considered overloaded when it shouldn't be overloaded.
        } else if (event instanceof TaskConcludedEvent ev) {
            ITask finishedTask = ev.getTask();
            handleTaskConcludedEvent(node, pid, finishedTask);
        }
        // Note: Updates internal state only sends data to user later
    }

    /**
     * This method is responsible for one-hop broadcasting the state of this node to it's neighbourhood.
     * @param node
     * @param protocolID
     */
    protected void broadcastStateChanges(Node node, int protocolID) {
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        wrkInfoLog(EVENT_WORKER_INFO_SEND, "Q_size=" + this.queue.size() + " rcv_Apps=" + this.recievedApplications.size() + " working=" + !(current == null));
        WorkerInfo wi = this.compileWorkerInfo();
        for (int i = 0; i < linkable.degree(); i++) {
            Node controller = linkable.getNeighbor(i);
            if (!controller.isUp()) {
                wrkInfoLog(EVENT_WORKER_INFO_SEND_FAILED, "target=" + controller.getID());
                continue;
            }
            addComunicationEnergyCost(wi);
            ((Transport) node.getProtocol(FastConfig.getTransport(Controller.getPid()))).
                    send(
                            node,
                            controller,
                            wi,
                            Controller.getPid()
                    );
        }

        this.changedWorkerState = false;
    }



    /**
     * This method handles the conclusion of a task that was offloaded to this node. It sends the result of completion
     * to the last node that offloaded the task to this node.
     * @param node
     * @param protocolID
     * @param task
     */
    protected void handleRemoteTaskFinish(Node node, int protocolID, ITask task){
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        Node handler = this.getNodeFromId(task.pollLastConnectionId(), linkable);

        // Clean up the data structures
        loseTaskInformation.remove(task.getId());

        if (handler == null || !handler.isUp()) {
            wrkErrLog("NO CTR FOR REMOTE TSK", "Node does not know Node="+task.getOriginalHandlerID() +" that requested task=" +task.getId()+", dropping task" );
        }else {
            wrkInfoLog(EVENT_TASK_FINISH,  "taskId=" + task.getId());
            TaskConcludedEvent taskconcluded = new TaskConcludedEvent(this.id, task.getAppID(), task.getClientID(), task.getOutputSizeBytes(), task);
            addComunicationEnergyCost(taskconcluded);
            ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            handler,
                            taskconcluded,
                            Worker.getPid()
                    );
        }
    }

    /**
     * This method handles the conclusion of a task that is managed on this node. It updates the completions of the task,
     * if all the tasks in the node have finished computing, this method it calls the method responsible for handling application
     * completions.
     * @param node
     * @param protocolID
     * @param finishedTask
     */
    protected void handleTaskFinish(Node node, int protocolID, ITask finishedTask){
        Application app = this.managedApplications.get(finishedTask.getAppID());
        app.addProgress(finishedTask.getId());
        finishedTask.addEvent(TaskHistory.TaskEvenType.COMPLETED, this.id, CommonState.getTime());
        tasksCompletedSinceLastCycle.add(finishedTask);
        if (app.isFinished()) {
            this.handleApplicationFinish(node, protocolID, app);
        }
    }

    /**
     * This method selects how to deal with a task that was completed based on whether the task was offloaded to this node
     * or if it was originally managed by this node.
     * @param node
     * @param protocolID
     * @param finishedTask
     */
    protected void handleTaskConcludedEvent(Node node, int protocolID, ITask finishedTask){
        if(finishedTask.getOriginalHandlerID()  != this.id){
            handleRemoteTaskFinish(node, protocolID, finishedTask);
        }else{
            handleTaskFinish(node, protocolID, finishedTask);
        }
    }

    /**
     * This handles the finishing of all tasks in an application. It removes the application from the node and sends the
     * results to the client that requested the application be processed.
     * @param node
     * @param protocolID
     * @param app
     */
    protected void handleApplicationFinish(Node node, int protocolID, Application app){
        managedApplications.remove(app.getAppID()); // If this is running then everything has been removed from the application.

        // Send results to client
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        Node client = this.getNodeFromId(app.getClientID(), linkable);

        if (!client.isUp()) return; // This happens task progress is lost.
        wrkInfoLog(EVENT_APP_FINISH, "appId=" + app.getAppID() + " deadlineWas="+app.getDeadline() + " finished=" + app.isFinished());
        AppConcludedEvent appConcluded = new AppConcludedEvent(this.id, app.getAppID(), app.getOutputDataSize());
        addComunicationEnergyCost(appConcluded);
        ((Transport) node.getProtocol(FastConfig.getTransport(Client.getPid()))).
                send(
                        node,
                        client,
                        appConcluded,
                        Client.getPid()
                );
    }

    protected abstract void handleTaskOffloadEvent(TaskOffloadEvent ev);

    /**
     * This method is responsible for handling the arrival of a new application to the node. If the node is overloaded
     * the task is dropped, the Client is not explicitly informed.
     * @param ev
     */
    protected void handleNewApplicationEvent(NewApplicationEvent ev) {
        Application app = ev.getApp();

        this.totalTasksRecieved++;

        if (this.getTotalNumberOfTasksInNode() + app.applicationSize() > qMAX) {
            droppedLastCycle++;
            totalDropped++;
            wrkInfoLog(EVENT_OVERLOADED_NODE, " DroppedApp=" + app.getAppID());
            return;
        }

        this.tasksRecievedSinceLastCycle++;

        wrkInfoLog(EVENT_NEW_APP_RECIEVED, " appId=" + ev.getAppID());
        app.setHandlerID(this.id);
        app.setArrivalTime(CommonState.getTime());
        this.managedApplications.put(app.getAppID(), app);
        this.recievedApplications.add(app);
        toAddSize += app.applicationSize();

        this.changedWorkerState = true;
    }

    /**
     * This method takes all applications that have been accepted by the node since the last time this method was called,
     * separates them int o tasks and adds them to the queue.
     */
    protected abstract void applicationSerialization();

    protected boolean awaitingSerialization() {
        return this.queue.isEmpty() && !this.recievedApplications.isEmpty();
    }

    /**
     * This method collects all the expired apps and removes them from the simulation.
     */
    protected void cleanExpiredApps() {
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
     * This method removes all apps in parameter list of apps removeApps from the simulation
     *
     * @param removeApps
     */
    protected void purgeApps(Set<Application> removeApps) {
        Iterator<Application> iterator = removeApps.iterator();
        while (iterator.hasNext()) {
            Application app = iterator.next();
            String id = app.getAppID();
            if (managedApplications.containsKey(id)) {
                managedApplications.remove(id);
                Log.info("Cleaning Application(" + id + "), Node " + this.getId() + "deadline(" + app.getDeadline() + ") expired. Curr Time:" + CommonState.getTime());
                if (current != null && Objects.equals(current.getAppID(), id)) this.current = null;
                iterator.remove(); // Remove the ID from the set as well
            }
        }
    }

    protected boolean isTaskLocal(ITask t) {
        return managedApplications.get(t.getAppID()) != null;
    }

    abstract void resetReceived();

    protected abstract boolean nextProcessableTask(Node node, int pid);

    protected int selectOffloadTargetPid(int targetIndex, Node target){
        int pid;
        if(((Worker) target.getProtocol(Worker.getPid())).isActive()){
            pid = Worker.getPid();
        }else if(((Cloud) target.getProtocol(Cloud.getPid())).isActive()){
            pid = Cloud.getPid();
        }else{
            wrkErrLog(EVENT_ERR_NO_TARGET_PID_AVAILABLE,"The target node is not a DAGWorker nor a Cloud. This should not happen. Killing the simulation.");
            throw new RuntimeException("The target node is not a DAGWorker nor a Cloud. This should not happen. Killing the simulation.");
        }
        return pid;
    }

    protected boolean validOffloadingInstructions(int offloadingTarget, Linkable linkable) {

        return offloadingTarget > 0 || offloadingTarget < linkable.degree();
    }
    protected LoseTaskInfo getOrGenerateLoseTaskInfo(Node node, ITask task) {
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
            wrkErrLog("ERROR", "Something went terribly wrong. A task that should not be in this node is being offloaded. Node: " + node.getID() + " Timestep: " + CommonState.getIntTime());
            return null;
        }
        return lti;
    }

    @Override
    public WorkerInfo compileWorkerInfo() {
        return new WorkerInfo(this.id, this.getTotalNumberOfTasksInNode(), this.unprocessedTasksInApps(), averageTaskSize(), processingPower, qMAX - this.getTotalNumberOfTasksInNode(), this.getLayer(), this.getProps().getCoordinates());

    }

    protected void resetDataStructures() {
        queue = new TreeSet<>(dependentTaskComparator); // Assuming no concurrency within node. If we want to handle multiple requests confirm trx safe.
        loseTaskInformation = new HashMap<>();
        recievedApplications = new LinkedList<>();
        managedApplications = new HashMap<>();
        current = null;

        tasksCompletedSinceLastCycle = new LinkedList<>();
        tasksToBeLocallyProcessed = new HashSet<>();

    }

    /**
     * This method computes the average task size.
     *
     * @return the average number of instructions of the tasks in a node.
     */
    protected double averageTaskSize() {
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

    protected void printParams() {
        //if(active)
        wrkDbgLog("Worker Params: NO_CORES<" + this.cpuNoCores + "> FREQ<" + this.cpuFreq + "> Q_MAX<" + this.qMAX + ">");
    }

    protected void addComunicationEnergyCost(Message m) {
        this.energyConsumed += m.getSize() * costOfCommPerByte;
    }
    protected void addProcessingEnergyCost(double bytesProcessed) {
        this.energyConsumed += bytesProcessed * costOfCommPerByte;
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
     *
     * @param id,       id of the node to be looked for
     * @param linkable, the {@link Linkable} object of this node
     * @return the node with the id or null if there is no node with the given id
     */
    protected Node getNodeFromId(int id, Linkable linkable) {
        for (int i = 0; i < linkable.degree(); i++) {
            Node n = linkable.getNeighbor(i);
            if (n.getID() == id) return n;
        }
        return null;
    }

    protected boolean idle() {
        return this.queue.isEmpty() && this.recievedApplications.isEmpty() && this.current == null; //  && (this.current == null || this.current.done())
    }

    public int isWorking() {
        return this.current == null ? 0 : 1;
    }
    /**
     * This method computes the amount of tasks currently in the system. Which includes the tasks awaiting processing in
     * the received applications. The tasks currently in the queue and the task being processed right now.
     *
     * @return the total amount of tasks in the node.
     */
    @Override
    public int getTotalNumberOfTasksInNode() {
        return this.queue.size() + toAddSize + ((current == null || current.done()) ? 0 : 1);
    }

    public double getEnergyConsumed() {
        return energyConsumed;
    }

    public void setEnergyConsumed(double energyConsumed) {
        this.energyConsumed = energyConsumed;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public int unprocessedTasksInApps() {
        return toAddSize; // this.recievedApplications.stream().map(Application::applicationSize).reduce(0, Integer::sum);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public SDNNodeProperties getProps() {
        return props;
    }

    @Override
    public void setProps(SDNNodeProperties props) {
        this.props = props;
    }

    @Override
    public void setProcessingPower(double processingPower) {
        this.processingPower = processingPower;
    }

    @Override
    public double getProcessingPower() {
        return processingPower;
    }

    @Override
    public void setCorrespondingController(Controller correspondingController) {
        this.correspondingController = correspondingController;
    }

    @Override
    public void setHasController(boolean hasController) {
        this.hasController = hasController;
    }

    @Override
    public boolean isHasController() {
        return hasController;
    }

    /**
     * @return The number of dropped tasks since the last time this method was called.
     */
    @Override
    public int getDroppedLastCycle() {
        int aux_droppedLastCycle = droppedLastCycle;
        droppedLastCycle = 0;
        return aux_droppedLastCycle;
    }

    /**
     * @return all the tasks dropped in this node since the beginning of the simulation.
     */
    @Override
    public int getTotalDropped() {
        return totalDropped;
    }

    @Override
    public int getTotalTasksRecieved() {
        return totalTasksRecieved;
    }

    @Override
    public int getLayer() {
        return layer;
    }

    public double getCpuFreq() {
        return cpuFreq;
    }

    public int getQueueCapacity() {
        return qMAX;
    }

    /**
     * @return all the tasks received since the last time this method was called. Independently of how they were handled.
     */
    @Override
    public int getTasksRecievedSinceLastCycle() {
        int aux_tasksRecievedSinceLastCycle = tasksRecievedSinceLastCycle;
        tasksRecievedSinceLastCycle = 0;
        return aux_tasksRecievedSinceLastCycle;
    }

    @Override
    public int getTotalTasksProcessed() {
        return totalTasksProcessed;
    }

    @Override
    public int getTotalTasksOffloaded() {
        return totalTasksOffloaded;
    }

    @Override
    public void wrkInfoLog(String event, String info) {
        Log.logInfo("WRK", this.id, event, info);

    }

    @Override
    public List<ITask> extractCompletedTasks() {
        List<ITask> aux = tasksCompletedSinceLastCycle;
        tasksCompletedSinceLastCycle = new LinkedList<>();
        return aux;
    }

    @Override
    public void wrkDbgLog(String msg) {
        Log.logDbg("WRK", this.id, "DEBUG", msg);
    }

    @Override
    public void wrkErrLog(String event, String msg) {
        Log.logErr("WRK", this.id, event, msg);
    }

    @Override
    public int getCpuNoCores() {
        return cpuNoCores;
    }

    @Override
    public double getAverageWaitingTime() {
        // TODO add the part corresponding to the waiting time of the tasks in the managed app.
        double managedAvgWaitTime = this.recievedApplications.stream().mapToDouble(Application::getTotalTaskSize).average().orElse(0);
        double queueAvgWaitTime = this.queue.stream().mapToDouble(ITask::getTotalInstructions).average().orElse(0);
        return (managedAvgWaitTime + queueAvgWaitTime) / processingPower;
    }
}
