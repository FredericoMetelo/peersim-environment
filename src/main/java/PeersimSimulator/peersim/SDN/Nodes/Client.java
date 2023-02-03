package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Util.Log;
import PeersimSimulator.peersim.SDN.Nodes.Events.NewTaskEvent;
import PeersimSimulator.peersim.SDN.Nodes.Events.TaskConcludedEvent;
import PeersimSimulator.peersim.SDN.Tasks.Task;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.transport.Transport;

import java.util.LinkedList;
import java.util.List;

public class Client implements CDProtocol, EDProtocol {

    private static final double BYTE_SIZE = 500e6;
    private static final double NO_INSTR = 200e6;
    private static final String PAR_NAME = "name";
    private static int pid;

    /**
     * Defines f this node is working as a Client
     * False - is not; True - is a client
     */
    private boolean active;

    // Keep average latency on this client.
    /**
     * List with Ids of the tasks awaiting response.
     */
    List<TaskInfo> tasksAwaiting;
    private float averageLatency;
    private int noResults;
    private int tick;

    private int id;

    public Client(String prefix) { // TODO queue size isn't doing much...
        pid = Configuration.getPid(prefix + "."+PAR_NAME ); //
        this.tasksAwaiting = new LinkedList<>();
        averageLatency = 0;
        noResults = 0;
        tick = 0;
        active = false;
    }

    @Override
    public Object clone() {
        Client svh=null;
        try {
            svh=(Client)super.clone();
        }
        catch( CloneNotSupportedException e ) {} // never happens
        return svh;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if(!active) return;
        tick++;
        // For each Node keeps a poisson distribution. Sends new request based on said distribution.
        // Sends Task to said node.
        // TODO Eventually use a poisson distribution
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        for(int i = 1; i < linkable.degree(); i++) {
            if (CommonState.r.nextDouble() < 1.0 || tick == 1) {
                Node target = linkable.getNeighbor(i);
                if (!target.isUp()) return; // This happens task progress is lost.
                Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
                Task task = new Task(BYTE_SIZE, NO_INSTR, wi.getId());
                tasksAwaiting.add(new TaskInfo(task.getId(), tick));
                Log.info("|CLT| TASK SENT to Node:<" + wi.getId() + ">");
                ((Transport) target.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                        send(
                                node,
                                target,
                                new NewTaskEvent(task),
                                Worker.getPid()
                        );
            }
        }

    }



    @Override
    public void processEvent(Node node, int pid, Object event) {
        if(!active) return;
        // Recieve answer from task sent.
        if(event instanceof TaskConcludedEvent ev){
            int endTick = tick;
            for(int i = 0; i < tasksAwaiting.size(); i++){
                if(tasksAwaiting.get(i).id.equals(ev.getTaskId())){
                    TaskInfo t = tasksAwaiting.remove(i);
                    int timeTaken = endTick - t.timeSent;
                    averageLatency = (averageLatency*noResults + timeTaken) / (++noResults);
                    Log.info("|CLT| TASK CONCLUDED: TaskId<" + ev.getTaskId() + "> NodeId:<" +t.id + ">");
                    return;
                }
            }
                System.out.print("Error this should nto happen task ended without having id in awaiting tasks.");

        }
    }

    public static int getPid(){
        return pid;
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

    @Override
    public String toString() {
        return "Client{" +

                ", tasksAwaiting=" + tasksAwaiting.size() +
                ", averageLatency=" + averageLatency +
                ", noResults=" + noResults +
                ", tick=" + tick +
                ", id=" + id +
                '}';
    }

    private class TaskInfo{
        protected String id;
        protected int timeSent;

        public TaskInfo(String id, int timeSent) {
            this.id = id;
            this.timeSent = timeSent;
        }
    }
}
