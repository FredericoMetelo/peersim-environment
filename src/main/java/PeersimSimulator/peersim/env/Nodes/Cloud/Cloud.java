package PeersimSimulator.peersim.env.Nodes.Cloud;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Events.CloudInfo;
import PeersimSimulator.peersim.env.Nodes.Events.TaskConcludedEvent;
import PeersimSimulator.peersim.env.Nodes.Events.TaskOffloadEvent;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.env.Records.Coordinates;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.TaskHistory;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.transport.Transport;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static PeersimSimulator.peersim.env.Nodes.Workers.Worker.EVENT_TASK_FINISH;

public class Cloud  implements CDProtocol, EDProtocol {


    private static final String PAR_NAME = "name";
    public static final String PAR_NO_VM = "no_vms";
    public static final String PAR_VMPROCESSING_POWER = "VMProcessingPower";
    private static final String EVENT_TASK_OFFLOAD = "RCV TASK OFFLOAD";
    public static final String EVENT_ERR_NO_CTR_FOR_REMOTE_TSK = "NO CTR 4 REMOTE TASK";
    private boolean active;

    SDNNodeProperties props;
    public int noVms;

    /**
     * The cloud internal id on the network
     */
    private int id;


    /**
     * Protocol identifier, obtained from config property {@link #PAR_NAME}.
     */
    private static int pid;

    private double defaultProcessingPower;
    private Queue<ITask> queue;
    private List<VM> vms;

    public Cloud(String prefix){
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        noVms = Configuration.getInt(prefix + "."+PAR_NO_VM, 1);
        defaultProcessingPower =  Configuration.getDouble(prefix + "." + PAR_VMPROCESSING_POWER);

        active = false;

    }

    @Override
    public Object clone() {
        Cloud svh = null;
        try {
            svh = (Cloud) super.clone();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if(!active) return;
        for (VM vm : this.vms){
            ITask t;
            vm.resetProcessingPower();

            do{
                if(vm.idle()) {
                    ITask next = this.selectNext();
                    vm.attributeTask(next);
                }
                t = vm.process();
                if(t != null){
                    this.handleFinishedTask(node, protocolID, t);
                }
            }while(t != null);
        }
    }

    @Override
    public void processEvent(Node node, int pid, Object event) {
        if(!active) return;
        if(event instanceof TaskOffloadEvent ev){
            handleTaskOffloadEvent(ev);
        }
    }

    public static int getPid() {
        return pid;
    }
    private void handleTaskOffloadEvent(TaskOffloadEvent ev) {
        ITask t = ev.getTask();
        cldInfoLog(EVENT_TASK_OFFLOAD, "taskId=" + t.getId());
        t.addEvent(TaskHistory.TaskEvenType.OFFLOADED, this.getId(), CommonState.getTime());
        queue.add(t);
    }

    private void handleFinishedTask(Node node, int protocolID, ITask task) {
        task.addEvent(TaskHistory.TaskEvenType.COMPLETED, this.getId(), CommonState.getTime());

        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        Node handler = this.getNodeFromId(task.pollLastConnectionId(), linkable);

        // Clean up the data structures
        if (handler == null || !handler.isUp()) {
            cldErrLog(EVENT_ERR_NO_CTR_FOR_REMOTE_TSK, "Node does not know Node="+task.getOriginalHandlerID() +" that requested task=" +task.getId()+", dropping task" );
        }else {
            cldInfoLog(EVENT_TASK_FINISH,  "taskId=" + task.getId());
            ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            handler,
                            new TaskConcludedEvent(this.id, task.getAppID(), task.getClientID(), task.getOutputSizeBytes(), task),
                            Worker.getPid()
                    );
        }
    }

    public int getId() {
        return id;
    }

    public void init(int id){
        this.id = id;
        queue = new LinkedList<>();
        vms = new ArrayList<>();
        for (int i = 0; i < noVms; i++) {
            vms.add(new VM(defaultProcessingPower));
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private ITask selectNext(){
       return queue.poll();
    }
    private Node getNodeFromId(int id, Linkable linkable){
        for (int i = 0; i < linkable.degree(); i++) {
            Node n = linkable.getNeighbor(i);
            if(n.getID() == id) return n;
        }
        return null;
    }

    public void cldDbgLog(String msg){
        Log.logDbg("CLD", this.id, "DEBUG", msg);
    }

    public void cldErrLog(String event, String msg){
        Log.logErr("CLD", this.id, event, msg);
    }

    public void cldInfoLog(String event, String info){
        Log.logInfo("CLD", this.id, event, info);

    }


    @Override
    public String toString() {
        return (active) ?
                "Cloud{" +
                " noVms=" + noVms +
                ", id=" + id +
                ", defaultProcessingPower=" + defaultProcessingPower +
                ", queue=" + queue +
                ", vms=" + vms +
                '}'
                : "Cloud{inactive}";
    }

    public CloudInfo cloudInfo() {
        double defaultProcessingPower = this.defaultProcessingPower;
        int queueSize = queue.size();
        int freeVMs = (int) vms.stream().filter(VM::idle).count();
        int totalVMs = noVms;
        int cloudId = id;

        return new CloudInfo(defaultProcessingPower, queueSize, freeVMs, totalVMs, cloudId);
    }

    public SDNNodeProperties getProps() {
        return props;
    }

    public void setProps(SDNNodeProperties props) {
        this.props = props;
    }

    // Private Classes =================================================================================================
    private class VM {
        ITask current;
        double processingPower;

        double processingPowerLeft;
        VM(double processingPower){
            this.processingPower = processingPower;
            this.processingPowerLeft = 0;
            this.current = null;
        }

        ITask process(){
            double remainingProcessingPower = processingPower;
            while (remainingProcessingPower > 0 && !this.idle()) {
                remainingProcessingPower = current.addProgress(remainingProcessingPower);
                if (current.done()) {
                    ITask t = current;
                    current = null;
                    return t;
                }
            }
            return null;
        }

        void attributeTask(ITask t){
            this.current = t;
        }

        void resetProcessingPower(){
            processingPowerLeft = 0;
        }

        public boolean idle() {
            return this.current == null;
        }


    }
}
