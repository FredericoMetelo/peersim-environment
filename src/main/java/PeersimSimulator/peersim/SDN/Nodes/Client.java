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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Client implements CDProtocol, EDProtocol {
    public static final int DEFAULT_TASK_SIZE = 500;
    public static final double DEFAULT_NO_INSTR = 200e6;
    public static final double DEFAULT_TASKARRIVALRATE = 0.01;

    private static final String PAR_BYTE_SIZE = "T";
    public final double BYTE_SIZE; // Mbytes

    private static final String PAR_NO_INSTR = "I";

    public final double NO_INSTR;

    private static final String PAR_CPI = "CPI";
    public final int CPI;

    private static final String PAR_NAME = "name";
    private static int pid;

    public static String PAR_TASKARRIVALRATE = "protocol.clt.taskArrivalRate";
    private final double TASK_ARRIVAL_RATE;

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

    private List<Long> nextArrival;

    // 0.10 % chance of new task being sent to worker per time tick.
    // Assuming poisson process therefore no change!
    private int id;

    public Client(String prefix) { // TODO queue size isn't doing much...
        pid = Configuration.getPid(prefix + "."+PAR_NAME ); //
        this.tasksAwaiting = new LinkedList<>();
        averageLatency = 0;
        noResults = 0;
        nextArrival = null;
        active = false;

        // Read Constants
        CPI = Configuration.getInt( prefix + "." + PAR_CPI, 1) ;
        BYTE_SIZE = Configuration.getInt( prefix + "." + PAR_BYTE_SIZE, DEFAULT_TASK_SIZE);
        NO_INSTR = Configuration.getDouble( prefix + "." + PAR_NO_INSTR, DEFAULT_NO_INSTR);
        TASK_ARRIVAL_RATE = Configuration.getDouble( prefix + "." + PAR_TASKARRIVALRATE, DEFAULT_TASKARRIVALRATE );

        printParams();

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
        // For each Node keeps a poisson distribution. Sends new request based on said distribution.
        // Sends Task to said node.
        // TODO Eventually use a poisson distribution
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        if(nextArrival == null) initTaskManagement(linkable.degree());

        for(int i = 1; i < linkable.degree(); i++) {
            if (nextArrival.get(i) <= CommonState.getTime()) {
                Node target = linkable.getNeighbor(i);
                if (!target.isUp()) return; // This happens task progress is lost.
                Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
                Task task = new Task(BYTE_SIZE, NO_INSTR, this.getId(), wi.getId());
                tasksAwaiting.add(new TaskInfo(task.getId(), CommonState.getTime()));
                Log.info("|CLT| TASK SENT to Node:<" + wi.getId() + "> FROM < " + this.getId()+">");
                ((Transport) target.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                        send(
                                node,
                                target,
                                new NewTaskEvent(task),
                                Worker.getPid()
                        );

                nextArrival.set(i, CommonState.getTime() + selectNextTime());
            }
        }

    }



    @Override
    public void processEvent(Node node, int pid, Object event) {
        if(!active) return;
        // Recieve answer from task sent.
        if(event instanceof TaskConcludedEvent ev){
             long endTick = CommonState.getTime();
            for(int i = 0; i < tasksAwaiting.size(); i++){
                if(tasksAwaiting.get(i).id.equals(ev.getTaskId())){
                    TaskInfo t = tasksAwaiting.remove(i);
                    long timeTaken = endTick - t.timeSent;
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

    public long selectNextTime(){
        /*
            Based on the formula from: https://preshing.com/20111007/how-to-generate-random-timings-for-a-poisson-process/
            From CDF of exponential function we have: 1 - e^{-lambda * x}. This is the probability no events occur on the
             first x time units. Then we invert that to go from probability to time and sample a probability with uniform
             RV.

             Note: The task arrival rate of this poisson process is lambda.
         */
        return (long) (-Math.log(CommonState.r.nextDouble()) / TASK_ARRIVAL_RATE);
    }
    private void initTaskManagement(int degree) {
        nextArrival = new ArrayList<Long>(degree);
        for (int i = 0; i < degree; i++) {
            nextArrival.add(selectNextTime());
        }
    }

    public boolean isActive() {
        return active;
    }


    // public static double getTaskArrivalRate() {
    //    return taskArrivalRate;
    // }

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
                ", tick=" + CommonState.getTime() +
                ", id=" + id +
                '}';
    }

    private class TaskInfo{
        protected String id;
        protected long timeSent;

        public TaskInfo(String id, long timeSent) {
            this.id = id;
            this.timeSent = timeSent;
        }
    }

    private void printParams(){
        //if(active)
            Log.dbg("Client Params: CPI<" + this.CPI + "> T<" + this.BYTE_SIZE+ "> I<"+ this.NO_INSTR+">" );
    }
}
