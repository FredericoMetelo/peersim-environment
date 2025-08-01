package PeersimSimulator.peersim.env.Nodes.Controllers;

import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Cloud.Cloud;
import PeersimSimulator.peersim.env.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.env.Records.*;
import PeersimSimulator.peersim.env.Records.Actions.Action;
import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Util.Log;

import java.util.*;

public abstract class AbstractController implements Controller {
    protected static final String EVENT_SEND_ACTION_RECIEVED = "SEND ACTION RECIEVED";
    protected static final String PAR_NAME = "name";
    protected static final int CONTROLLER_ID = 0;
    protected static int pid;
    /**
     * Defines f this node is working as a BasicController
     * False - is not; True - is a BasicController
     */
    protected boolean active;
    protected Worker correspondingWorker;
    protected OffloadInstructions currentInstructions;
    /**
     * selectedNode represents the latest selected ID of node, between [0, N]
     * <p>
     * The selection is based on an Evnet Queue, the node with the oldest event is selected. All other events of that
     * node are removed from the Queue.
     */
    protected int selectedNode;
    protected int cycle;
    protected int id;
    protected LinkedList<Integer> nodeUpdateEventList;
    /**
     * up and stop are the variables responsible for the flow management of the application.
     * up - represents the state of the application, if the application is running up will be true and the controller will accept requests from the agent.tap:
     * stop - is the variable that says if the application is executing and can't retrieve data. Every 100 ticks the simulation checks if the event queue is not empty.
     * And selects the next node to offload from.
     */
    protected volatile boolean stop;
    protected volatile boolean up;

    SDNNodeProperties props;
    List<WorkerInfo> workerInfo;

    List<String> updatesAvailable;
    Map<String, List<Integer>> pathsToOtherControllers;




    public AbstractController() {
        active = false;
        selectedNode = this.getId(); // Ignores the controller.
        workerInfo = new ArrayList<>();
        nodeUpdateEventList = new LinkedList<>();
        updatesAvailable = new LinkedList<>();
        stop = true;
        up = false;


    }

    public static int getPid() {
        return pid;
    }

    public static void setPid(int pid) {
        AbstractController.pid = pid;
    }

    @Override
    public Object clone() {
        AbstractController svh = null;
        try {
            svh = (AbstractController) super.clone();
            svh.workerInfo = new ArrayList<>();
            svh.updatesAvailable = new LinkedList<>();
            svh.nodeUpdateEventList = new LinkedList<>();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if (!active) return;
        if (workerInfo.isEmpty()) initializeWorkerInfo(node, Controller.getPid());
        up = true;
    }

    /**
     * This method is responsible for sending the action to the respective worker.
     * @param action
     * @return
     */
    @Override
    public abstract SimulationData sendAction(Action action);

    /**
     * This method is responsible for extracting the completed tasks from the worker since the last time-step.
     * @return List of completed tasks since the last time-step.
     */
    @Override
    public List<ITask> extractCompletedTasks() {
        return this.correspondingWorker.extractCompletedTasks();
    }

    protected WorkerInfo getWorkerInfo(int nodeId) {
        for (WorkerInfo w : workerInfo) {
            if (w.getId() == nodeId)
                return w;
        }
        return null;
    }

    @Override
    public void processEvent(Node node, int pid, Object event) {
        if (!active) return;
        // Recieve Update from Node.
        if (event instanceof WorkerInfo wi) {
            updateNode(wi);
        }
    }

    @Override
    public Worker getCorrespondingWorker() {
        return correspondingWorker;
    }

    @Override
    public void setCorrespondingWorker(Worker correspondingWorker) {
        this.correspondingWorker = correspondingWorker;
    }

    /**
     * This method receives the state information from the neighbours and stores them in the workerInfo list.
     * @param newWi
     */
    protected void updateNode(WorkerInfo newWi) {
        for (WorkerInfo oldWi : workerInfo) {
            if (oldWi.getId() == newWi.getId()) {
                ctrInfoLog(EVENT_WORKER_INFO_UPDATE, "id=" + newWi.getId() + ", Q_size=" + oldWi.getTotalTasks() + "->" + newWi.getTotalTasks() + " rcv_tasks=" + oldWi.getUnprocessedApplications() + "->" + newWi.getUnprocessedApplications());
                oldWi.setUnprocessedApplications(newWi.getUnprocessedApplications());
                oldWi.setQueueSize(newWi.getQueueSize());
                oldWi.setNodeProcessingPower(newWi.getNodeProcessingPower());
                oldWi.setAverageTaskSize(newWi.getAverageTaskSize());
                oldWi.setLayer(newWi.getLayer());
                oldWi.setLastKnownPosition(newWi.getLastKnownPosition());
                oldWi.setFreeTaskSlots(newWi.getFreeTaskSlots());
                nodeUpdateEventList.addLast(oldWi.getId());
                return;
            }
        }
        ctrInfoLog(EVENT_WORKER_INFO_ADD, "id=" + newWi.getId() + ", Q_size=" + newWi.getTotalTasks() + "rcv_Apps=" + newWi.getUnprocessedApplications());

        // Means no node with given Id has sent information to the BasicController yet.
        // Only happens with nodes that joined later. All nodes known from beginning are init with a 0 (?).
        workerInfo.add(newWi);
    }

    /**
     * This method will generate an entry for each of the nodes in the network excluding the controller node.
     * Note: In the future perhaps have two different modes. One where the BasicController participates in the network,
     * another where the controller is just managing the network (curretn implementation is this where the controller is
     * just managing the network).
     *
     * @param node
     * @param protocolID
     */

    public void initializeWorkerInfo(Node node, int protocolID) {


        double default_task_size = 200e6; // FIX THIS TODO
        double default_CPU_FREQ = 1e7;    // FIX THIS TODO
        int default_CPU_NO_CORES = 4;     // FIX THIS TODO

        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        for (int i = 0; i < linkable.degree(); i++) {
            Node target = linkable.getNeighbor(i);
            /*if(target.getID() == this.getId()){
                workerInfo.add(correspondingWorker.compileWorkerInfo());
            }*/
            if (!target.isUp()) return; // This happens task progress is lost.

            Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
            workerInfo.add(
                    new WorkerInfo(wi.getId(), 0, 0, default_task_size, Math.floor(default_CPU_NO_CORES * default_CPU_FREQ), wi.getQueueCapacity(), -2, null) // This should technically be a request...
            );
        }
    }

    //=== Reward Function
    @Override
    public abstract SimulationData compileSimulationData(Object neighbourIndex, int sourceID, boolean success);

    //======================================================================================================
    @Override
    public List<WorkerInfo> getWorkerInfo() {
        updateNode(this.correspondingWorker.compileWorkerInfo());
        updateCloudIfExists();
        return workerInfo;
    }

    private void updateCloudIfExists() {
        if (correspondingWorker.getCloudAccess() == 1) {
            WorkerInfo cloudInfo = new WorkerInfo(
                    Network.size() - 1,
                    0,
                    0,
                    0,
                    0,
                    correspondingWorker.getQueueCapacity(),
                    -1,
                    ((Cloud) Network.get(Network.size() - 1).getProtocol(Cloud.getPid())).getProps().getCoordinates()
            );
            // cloud is always the last node in the workerInfo list. because it is always the last node in the network.
            workerInfo.set(workerInfo.size() - 1, cloudInfo);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public List<Integer> getQ() {
        //int start = 1; // By definition can't have less than 3 nodes. For convenience
        updateThisNodeInfo();
        updateCloudIfExists();
        List<Integer> q = new ArrayList<>(this.workerInfo.stream().map(WorkerInfo::getTotalTasks).toList());
        return q;
    }
    private List<Integer> getKnownFreeSpaceNeighbours() {
        updateThisNodeInfo();
        updateCloudIfExists();
        List<Integer> q = new ArrayList<>(this.workerInfo.stream().map(WorkerInfo::getFreeTaskSlots).toList());
        return q;
    }

    protected double getAverageResponseTime() {
        if (this.correspondingWorker == null) {
            return 0;
        }
        return this.correspondingWorker.getAverageResponseTime();
    }
    /**
     * This method moves the current node to the first position of the list. This is allows the agent to always find the node in the correct position and avoids mistakes
     */
    private void updateThisNodeInfo() {
        if (this.workerInfo.get(0).getId() == this.getId()) {
            this.workerInfo.set(0, this.correspondingWorker.compileWorkerInfo());
            return;
        }
        for (int i = 0; i < this.workerInfo.size(); i++) {
            if (this.workerInfo.get(i).getId() == this.getId()) {
                WorkerInfo wi = this.correspondingWorker.compileWorkerInfo();
                this.workerInfo.remove(i);
                this.workerInfo.add(0, wi);
                return;
            }
        }
    }

    private List<Double> computeDistancesToNeighbours() {
        return this.workerInfo.stream().map(wi -> props.distanceTo(wi.getLastKnownPosition())).toList(); // TODO this is also broken...
    }

    @Override
    public PartialState getState() {
        ctrDbgLog("Acquiring state");
        // stop = true; Set the await action to block on next iter.
        SDNNodeProperties props = correspondingWorker.getProps();

        // using a list here is an hotfix to a limitation of gymnsaium. That for some reason does not allow continuous
        // values outside of arrays...
        List<Double> l = new LinkedList<>();
        l.add(props.getTRANSMISSION_POWER());
        return new PartialState(this.getId(),
                this.correspondingWorker.getTotalNumberOfTasksInNode(),
                this.getQ(),
                this.getKnownFreeSpaceNeighbours(),
                correspondingWorker.getProcessingPower(),
                correspondingWorker.getAverageWaitingTime(),
                correspondingWorker.getLayer(),
                new Coordinates(props.getX(), props.getY()),
                this.computeDistancesToNeighbours(),
                props.getBANDWIDTH(),
                l,
                (this.workerInfo == null || this.workerInfo.isEmpty())? 0 : this.workerInfo.size(),
                this.correspondingWorker.getNextTaskInfo(),
                this.correspondingWorker.getAllTaskInfo()
        );
    }

//    @Override
//    public DebugInfo getDebugInfo() {
//        List<Integer> ids = new LinkedList<>();
//        List<Integer> droppedFromLastCycle = new LinkedList<Integer>();
//        List<Integer> droppedTotal = new LinkedList<Integer>();
//        List<Integer> totalTasksRecieved = new LinkedList<Integer>();
//        List<Integer> tasksRecievedSinceLastCycle = new LinkedList<Integer>();
//        List<Integer> totalTasksProcessed = new LinkedList<Integer>();
//        List<Integer> totalTasksOffloaded = new LinkedList<Integer>();
//        List<Boolean> workerInvariant = new LinkedList<Boolean>();
//        List<Double> averageResponseTime = new LinkedList<Double>();
//
//        for (int i = 0; i < Network.size(); ++i) {
//            Worker w = ((Worker) Network.get(i).getProtocol(Worker.getPid()));
//
//            ids.add(w.getId());
//            droppedTotal.add(w.getTotalDropped());
//            droppedFromLastCycle.add(w.getDroppedLastCycle());
//            totalTasksRecieved.add(w.getTotalTasksRecieved());
//            tasksRecievedSinceLastCycle.add(w.getTasksRecievedSinceLastCycle());
//            totalTasksProcessed.add(w.getTotalTasksProcessed());
//            totalTasksOffloaded.add(w.getTotalTasksOffloadedFromNode());
//            averageResponseTime.add(w.getAverageResponseTime());
//            workerInvariant.add(
//                    w.getTotalTasksRecieved() == w.getTotalDropped() + w.getTotalTasksProcessed() + w.getTotalTasksOffloadedFromNode() + w.getTotalNumberOfTasksInNode()
//            );
//        }
//        return new DebugInfo(
//                CommonState.getTime(),
//                ids,
//                droppedTotal,
//                droppedFromLastCycle,
//                totalTasksRecieved,
//                tasksRecievedSinceLastCycle,
//                totalTasksProcessed,
//                totalTasksOffloaded,
//                workerInvariant,
//                averageResponseTime);
//    }

    @Override
    public boolean sendFLUpdate(FLUpdate update) {
        // Request the worker to send the updates to their neighbours through their interface.
        return this.correspondingWorker.sendFLUpdate(update);
    }

    @Override
    public void setPathsToOtherControllers(Map<String, List<Integer>> pathsToOtherControllers) {
        this.pathsToOtherControllers = pathsToOtherControllers;
    }

    @Override
    public Map<String, List<Integer>> getPathsToOtherControllers() {
        return pathsToOtherControllers;
    }

    @Override
    public void setUpdateAvailable(String key) {
        this.updatesAvailable.add(key);
    }
    @Override
    public List<String> getUpdatesAvailable() {
        List<String> availableUpdates = this.updatesAvailable;
        this.updatesAvailable = new LinkedList<>();
        ctrInfoLog("FL-COLLECT", "Collected " + availableUpdates.size() + " updates.");
        return availableUpdates;
    }

    @Override
    public String toString() {
        return (active) ? "BasicController{" +
                "id=" + id +
                ", workerInfo=" + workerInfo +
                ", currentInstructions=" + currentInstructions +
                ", active=" + active +
                ", stop=" + stop +
                ", up=" + up +
                '}'
                : "BasicController{inactive}";
    }

    @Override
    public void setProps(SDNNodeProperties prot) {
        this.props = prot;
    }

    public void ctrInfoLog(String event, String info) {
        Log.logInfo("CTR", this.id, event, info);

    }


    public void ctrDbgLog(String msg) {
        Log.logDbg("CTR", this.id, "DEBUG", msg);
    }

    public void ctrErrLog(String msg) {
        Log.logErr("CTR", this.id, "ERROR", msg);
    }


}
