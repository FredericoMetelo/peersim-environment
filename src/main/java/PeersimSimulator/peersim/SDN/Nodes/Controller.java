package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Links.Log;
import PeersimSimulator.peersim.SDN.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.SDN.Nodes.Events.SpringEvents.ActionReadyEvent;
import PeersimSimulator.peersim.SDN.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.SDN.Records.Action;
import PeersimSimulator.peersim.SDN.Records.EnvState;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.transport.Transport;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.List;


public class Controller implements CDProtocol, EDProtocol, ApplicationListener<ActionReadyEvent> {
    private static final String PAR_NAME = "name";
    private static final int CONTROLLER_ID = 0;
    private static int pid;

    /**
     * Defines f this node is working as a Controller
     * False - is not; True - is a Controller
     */
    private boolean active;

    int selectedNode;
    List<WorkerInfo> workerInfo;
    private int id;

    private volatile boolean stop;
    public Controller(String prefix) {
        pid = Configuration.getPid(prefix+ "."+PAR_NAME);
        workerInfo = new ArrayList<>();
        active = false;
        stop = true;
    }

    @Override
    public Object clone() {
        Controller svh=null;
        try {
            svh=(Controller)super.clone();
            svh.workerInfo = new ArrayList<>();
        }
        catch( CloneNotSupportedException e ) {} // never happens
        return svh;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if(!active) return;
        if(workerInfo.isEmpty()) initializeWorkerInfor(node, Controller.getPid());
        stop = true;
        awaitAction();
    }



    public boolean sendAction(Action a) {
        if(!active || a == null || a.noTasks() < 0 || a.nodeId() < 0) return false;

        // Pick Node to be offloaded. Inform Python   of the WorkerInfo in question. Get the Action for that node to execute.
        /*
         * This will initially be done in round-robin style across all the nodes except the controller.
         * Updating one node per cycle.
         */
        Node node = Network.get(CONTROLLER_ID);
        if(workerInfo.isEmpty()) initializeWorkerInfor(node, Controller.getPid());

        int linkableID = FastConfig.getLinkable(Controller.getPid());
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        if (linkable.degree() > 0) {
            selectedNode = (selectedNode + 1) % linkable.degree();
            Node selectedWorker = linkable.getNeighbor(selectedNode);

            if(!selectedWorker.isUp() || workerInfo.isEmpty()) return false;

            int targetNode = a.nodeId();
            int noTasks = a.noTasks();

            Log.info("ACTION: SRC<" + this.getId() + "> to TARGET:<" +targetNode + "> offload (" + noTasks + ")");

            ((Transport)selectedWorker.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            selectedWorker,
                            new OffloadInstructions(targetNode, noTasks), // TODO get the values for this constructor from python!!!
                            Worker.getPid()
                    );

            // allow progress
            stop = false;
            return true;

        }
        return false;

    }

    @Override
    public void onApplicationEvent(ActionReadyEvent event) {

    }


    @Override
    public void processEvent(Node node, int pid, Object event) {
        if(!active) return;
        // Recieve Update from Node.
        if(event instanceof WorkerInfo){
            WorkerInfo wi = (WorkerInfo) event;

            updateNode(wi);
        }
    }

    //======================================================================================================
    // Interface Methods
    //======================================================================================================

    public List<WorkerInfo> getWorkerInfo() {
        return workerInfo;
    }

    public void setWorkerInfo(List<WorkerInfo> workerInfo) {
        this.workerInfo = workerInfo;
    }

    public EnvState getState(){
        Log.dbg("Acquiring state");
        // stop = true; Set the await action to block on next iter.
        return new EnvState(this.workerInfo, this.selectedNode, this.workerInfo.get(selectedNode).getW());
    }

    /**
     * Spin waits for the next action.
     */
    private void awaitAction() {
        Log.dbg("Start Waiting");
        while (stop) Thread.onSpinWait();
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
    public static int getPid(){
        return pid;
    }

    //======================================================================================================
    // Private Methods
    //======================================================================================================

    private void updateNode(WorkerInfo newWi){
        for (WorkerInfo oldWi : workerInfo) {
            if(oldWi.getId() == newWi.getId()){
                Log.info("|CTR| WORKER-INFO UPDATE: SRC<"+ newWi.getId() + "> Qi<" +oldWi.getQueueSize()+"->" +newWi.getQueueSize() + "> Wi <" +oldWi.getW()+"->"  + newWi.getW() + ">");

                oldWi.setW(newWi.getW());
                oldWi.setQueueSize(newWi.getQueueSize());
                return;
            }
        }
        Log.info("|CTR| WORKER-INFO ADD: SRC<"+ newWi.getId() + "> Qi<" +"->" +newWi.getQueueSize() + "> Wi <" + newWi.getW() + ">");
        // Means no node with given Id has sent information to the Controller yet.
        // Only happens with nodes that joined later. All nodes known from beginning are init with a 0 (?).
        workerInfo.add(newWi);
    }
    private void initializeWorkerInfor(Node node, int protocolID) {
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        for(int i = 0; i < linkable.degree(); i++){
            Node target = linkable.getNeighbor(i);
            if (!target.isUp()) return; // This happens task progress is lost.
            Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
            workerInfo.add(
                    new WorkerInfo(wi.getId(), 0, 0)
            );
        }
    }



}
