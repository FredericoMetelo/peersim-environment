package PeersimSimulator.peersim.env.Nodes;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Records.Coordinates;
import PeersimSimulator.peersim.env.Records.DebugInfo;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.env.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.env.Records.Action;
import PeersimSimulator.peersim.env.Records.PartialState;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;

import java.util.*;


public class Controller implements CDProtocol, EDProtocol {

    private static final String EVENT_SEND_ACTION_RECIEVED = "SEND ACTION RECIEVED";

    private static final String PAR_NAME = "name";
    public static final String EVENT_WORKER_INFO_UPDATE = "WORKER-INFO UPDATE";
    public static final String EVENT_WORKER_INFO_ADD = "WORKER-INFO ADD";

    private static final int CONTROLLER_ID = 0;
    private static int pid;

    /**
     * Defines f this node is working as a Controller
     * False - is not; True - is a Controller
     */
    private boolean active;

    private Worker correspondingWorker;

    private OffloadInstructions currentInstructions;

    SDNNodeProperties props;

    /**
     * selectedNode represents the latest selected ID of node, between [0, N]
     * <p>
     * The selection is based on an Evnet Queue, the node with the oldest event is selected. All other events of that
     * node are removed from the Queue.
     */
    int selectedNode;
    int cycle;
    List<WorkerInfo> workerInfo;
    private int id;

    private LinkedList<Integer> nodeUpdateEventList;

    /**
     * up and stop are the variables responsible for the flow management of the application.
     * up - represents the state of the application, if the application is running up will be true and the controller will accept requests from the agent.tap:
     * stop - is the variable that says if the application is executing and can't retrieve data. Every 100 ticks the simulation checks if the event queue is not empty.
     * And selects the next node to offload from.
     */
    private volatile boolean stop, up;

    public Controller(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        workerInfo = new ArrayList<>();
        active = false;
        up = false;
        stop = true;
        // Read Constants

        selectedNode = this.getId(); // Ignores the controller.
        nodeUpdateEventList = new LinkedList<>();
        // printParams();

    }

    @Override
    public Object clone() {
        Controller svh = null;
        try {
            svh = (Controller) super.clone();
            svh.workerInfo = new ArrayList<>();
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
     * Passes the action to send to the respective worker to the Controller
     * @param a the action tobe sent, should consist of an index in the node's neighbourhood
     * @return the reward attributed to said action.
     */
    public double sendAction(Action a) {
        if (!active || a == null || a.controllerId() < 0 || a.neighbourIndex() < 0) return -1;
        ctrDbgLog(a.toString());
        Node node = Network.get(this.getId());
        int linkableID = FastConfig.getLinkable(Controller.getPid());
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        if (linkable.degree() <= 0 || a.neighbourIndex() >=linkable.degree()) return -1;
        int neighbourIndex = a.neighbourIndex();
        ctrInfoLog(EVENT_SEND_ACTION_RECIEVED, "TARGET_INDEX="+neighbourIndex );
        double reward = 0; //calculatReward(linkable, node, targetNode, 1);
        this.currentInstructions = new OffloadInstructions(neighbourIndex);
        this.correspondingWorker.offloadInstructions(Worker.getPid(), this.currentInstructions);
        // allow progress
        stop = false;
        return reward;
    }

    public List<ITask> extractCompletedTasks(){
        return this.correspondingWorker.extractCompletedTasks();
    }


    private WorkerInfo getWorkerInfo(int nodeId) {
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

    public Worker getCorrespondingWorker() {
        return correspondingWorker;
    }

    public void setCorrespondingWorker(Worker correspondingWorker) {
        this.correspondingWorker = correspondingWorker;
    }

//======================================================================================================
    // Private Methods
    //======================================================================================================

    //=== Node Management

    /**
     * Spin waits for the next action.
     */
    private void awaitAction() {
        ctrDbgLog("Start Waiting Time<" + CommonState.getTime() + ">");
        while (stop) Thread.onSpinWait();
    }


    public OffloadInstructions requestOffloadInstructions() {
        stop = true;
        awaitAction();
        OffloadInstructions oi = currentInstructions;
        currentInstructions = null;
        return oi;
    }

    private void updateNode(WorkerInfo newWi) {
        for (WorkerInfo oldWi : workerInfo) {
            if (oldWi.getId() == newWi.getId()) {
                ctrInfoLog(EVENT_WORKER_INFO_UPDATE, "id="+newWi.getId()+", Q_size="+ oldWi.getTotalTasks() + "->" + newWi.getTotalTasks() + " rcv_tasks=" + oldWi.getUnprocessedApplications() + "->" + newWi.getUnprocessedApplications());
                oldWi.setUnprocessedApplications(newWi.getUnprocessedApplications());
                oldWi.setQueueSize(newWi.getQueueSize());
                oldWi.setNodeProcessingPower(newWi.getNodeProcessingPower());
                oldWi.setAverageTaskSize(newWi.getAverageTaskSize());
                oldWi.setLayer(newWi.getLayer());
                nodeUpdateEventList.addLast(oldWi.getId());
                return;
            }
        }
        ctrInfoLog(EVENT_WORKER_INFO_ADD, "id="+newWi.getId()+", Q_size="+ newWi.getTotalTasks() + "rcv_Apps=" + newWi.getUnprocessedApplications());

        // Means no node with given Id has sent information to the Controller yet.
        // Only happens with nodes that joined later. All nodes known from beginning are init with a 0 (?).
        workerInfo.add(newWi);
    }

    /**
     * This method will generate an entry for each of the nodes in the network excluding the controller node.
     * Note: In the future perhaps have two different modes. One where the Controller participates in the network,
     * another where the controller is just managing the network (curretn implementation is this where the controller is
     * just managing the network).
     *
     * @param node
     * @param protocolID
     */
    void initializeWorkerInfo(Node node, int protocolID) {
        // TODO: It woud be more reasonable to add Workers as they communicate their WorkerInfo.
        //  For now I'll leave it with a default value but proper developement would recommend using the method that
        //  does not offload until some worker is seen and that will only consider the workers that communicated with it
        //  their WorkerInfo

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
                    new WorkerInfo(wi.getId(), 0, 0, default_task_size, Math.floor(default_CPU_NO_CORES * default_CPU_FREQ), wi.qMAX, -2, null) // This should technically be a request...
            );
        }
    }

    //=== Reward Function


    //=== Logging and Debugging
    /*private void printParams() {
        //if(active)
        // ctrDbgLog("Controller Params: r_u<" + this.UTILITY_REWARD + "> X_d<" + this.DELAY_WEIGHT + "> X_o<" + this.OVERLOAD_WEIGHT + ">");
    }*/

    //======================================================================================================
    // Interface Methods

    //======================================================================================================
    public List<WorkerInfo> getWorkerInfo() {
        return workerInfo;
    }

    public boolean isActive() {
        return active;
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



    public List<Integer> getQ() {
        //int start = 1; // By definition can't have less than 3 nodes. For convenience
        return this.workerInfo.stream().map(WorkerInfo::getTotalTasks).toList();
    }
    private List<Double> computeDistancesToNeighbours() {
        return this.workerInfo.stream().map(wi -> props.distanceTo(wi.getLastKnownPosition())).toList(); // TODO this is also broken...
    }


    public PartialState getState() {
        ctrDbgLog("Acquiring state");
        // stop = true; Set the await action to block on next iter.
        SDNNodeProperties props = correspondingWorker.getProps();
        // TODO deal with the fact wi might be null!!!! Btw I need to compute distance, I'm not entirely sure how to best do this. AS the
         // workers may be moving and there is no way they broadcast their position to the neighbourhood. I could have the controller
        //int offloadable_tasks = wi.getW_i();
        return new PartialState(this.selectedNode,
                this.getQ(),
                correspondingWorker.getProcessingPower(),
                correspondingWorker.getAverageWaitingTime(),
                correspondingWorker.getLayer(),
                new Coordinates(props.getX(), props.getY()),
                this.computeDistancesToNeighbours(),
                props.getBANDWIDTH(),
                props.getTRANSMISSION_POWER());
    }



    public DebugInfo getDebugInfo() {
        List<Integer> droppedFromLastCycle = new LinkedList<Integer>();
        List<Integer> droppedTotal = new LinkedList<Integer>();
        List<Integer> totalTasksRecieved = new LinkedList<Integer>();
        List<Integer> tasksRecievedSinceLastCycle = new LinkedList<Integer>();
        List<Integer> totalTasksProcessed = new LinkedList<Integer>();
        List<Integer> totalTasksOffloaded = new LinkedList<Integer>();
        List<Boolean> workerInvariant = new LinkedList<>();

        for (int i = 0; i < Network.size(); ++i) {
            Worker w = ((Worker) Network.get(i).getProtocol(Worker.getPid()));
            droppedTotal.add(w.getTotalDropped());
            droppedFromLastCycle.add(w.getDroppedLastCycle());
            totalTasksRecieved.add(w.getTotalTasksRecieved());
            tasksRecievedSinceLastCycle.add(w.getTasksRecievedSinceLastCycle());
            totalTasksProcessed.add(w.getTotalTasksProcessed());
            totalTasksOffloaded.add(w.getTotalTasksOffloaded());
            workerInvariant.add(
                    w.getTotalTasksRecieved() == w.getTotalDropped() + w.getTotalTasksProcessed() + w.getTotalTasksOffloaded() + w.getNumberOfTasks()
            );
        }
        return new DebugInfo(this.selectedNode,
                CommonState.getTime(),
                droppedTotal,
                droppedFromLastCycle,
                totalTasksRecieved,
                tasksRecievedSinceLastCycle,
                totalTasksProcessed,
                totalTasksOffloaded,
                workerInvariant);
    }

    @Override
    public String toString() {
        return (active)?"Controller{" +
                "id=" + id +
                ", workerInfo=" + workerInfo +
                ", currentInstructions=" + currentInstructions +
                ", active=" + active +
                ", stop=" + stop +
                ", up=" + up +
                '}'
                : "Controller{inactive}";
    }


    public void ctrInfoLog(String event, String info){
        Log.logInfo("CTR", this.id, event, info);

    }
    public void ctrDbgLog(String msg){
        Log.logDbg("CTR", this.id, "DEBUG", msg);
    }
    public void ctrErrLog(String msg){
        Log.logErr("CTR", this.id, "ERROR", msg);
    }
}