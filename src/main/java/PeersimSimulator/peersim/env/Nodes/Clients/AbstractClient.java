package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Nodes.Events.AppConcludedEvent;
import PeersimSimulator.peersim.env.Nodes.Events.NewApplicationEvent;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.env.Nodes.Workers.WorkerInitializer;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.transport.Transport;

import java.util.*;

public abstract class AbstractClient implements Client {

    protected static int pid;

    protected double[] BYTE_SIZE; // Mbytes
    protected double averageByteSize;
    protected double[] NO_INSTR;
    protected double[] taskCumulativeProbs;
    protected int maxDeadline;
    protected int[] CPI;
    protected double[] dagCumulativeProbs;
    /**
     * Defines f this node is working as a DAGClient
     * False - is not; True - is a client
     */
    protected boolean active;
    protected float averageLatency;
    protected int noResults;
    protected List<Long> nextArrival;
    protected double averageTaskSize;

    protected final int[] layers;
    protected final int numberOfTasks;
    protected final double TASK_ARRIVAL_RATE;

    // 0.10 % chance of new task being sent to worker per time tick.
    // Assuming poisson process therefore no change!
    protected int id;
    /**
     * List with Ids of the tasks awaiting response.
     */
    List<AppInfo> tasksAwaiting;

    public AbstractClient(String prefix) {
        pid = Configuration.getPid(prefix + "."+PAR_NAME ); //

        // Read Constants
        maxDeadline = Configuration.getInt(prefix + "." +PAR_MAX_DEADLINE, DEFAULT_MAX_DEADLINE);
        TASK_ARRIVAL_RATE = Configuration.getDouble( prefix + "." + PAR_TASKARRIVALRATE, DEFAULT_TASKARRIVALRATE );
        numberOfTasks = Configuration.getInt( prefix + "." + PAR_NO_TASKS, DEFAULT_NUMBEROFTASKS);
        String[] _layers = Configuration.getString(WorkerInitializer.PAR_NO_NODES_PER_LAYERS).split(",");
        layers = Arrays.stream(_layers).mapToInt(Integer::parseInt).toArray();


        getTaskMetadata(prefix);
    }

    /**
     * Binary search for the index of the first bigger value. This is ChatGPT generated but I've confirmed by hand it
     * works as intended.
     *
     * @param sortedArray
     * @param target
     * @return the index of the first bigger value.
     */
    protected static int findFirstBigger(double[] sortedArray, double target) {
        int left = 0;
        int right = sortedArray.length - 1;
        int result = -1;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            if (sortedArray[mid] > target) {
                result = mid;
                right = mid - 1; // Continue searching in the left half
            } else {
                result = mid;
                left = mid + 1; // Continue searching in the right half
            }
        }

        return result;
    }

    protected abstract void getTaskMetadata(String prefix);

    @Override
    public Object clone() {
        AbstractClient svh = null;
        try {
            svh = (AbstractClient) super.clone();
            svh.clientInit();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }

    @Override
    public void nextCycle(Node node, int protocolID) { // Change this to be across the network and only send to the first layer. Add the variables to the configs.
        if (!active) return;
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        if (nextArrival == null) initTaskManagement(linkable.degree());

        for (int i = 0; i < linkable.degree(); i++) {
            if (nextArrival.get(i) <= CommonState.getTime()
                    && ((Worker) linkable.getNeighbor(i).getProtocol(Worker.getPid())).getLayer() == 0) { // Not sure of the legality of this...
                Node target = linkable.getNeighbor(i);
                if (!target.isUp()) continue; // This happens task progress is lost.
                Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
                Application app = generateApplication((int) target.getID());
                tasksAwaiting.add(new AppInfo(app.getAppID(), CommonState.getTime(), app.getDeadline()));
                cltInfoLog(EVENT_TASK_SENT, "target=" + wi.getId());
                ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                        send(
                                node,
                                target,
                                new NewApplicationEvent(app.getAppID(), this.id, app.getInitialDataSize(), app),
                                Worker.getPid()
                        );

                nextArrival.set(i, CommonState.getTime() + selectNextTime());
            }
        }
    }

    protected int pickTaskType() { // Btw variable is a randDouble not a randInt
        double aux = CommonState.r.nextDouble(0, numberOfTasks);
        return AbstractClient.findFirstBigger(taskCumulativeProbs, aux);
    }



    @Override
    public void processEvent(Node node, int pid, Object event) {
        if (!active) return;
        // Recieve answer from task sent.
        if (event instanceof AppConcludedEvent ev) {
            long endTick = CommonState.getTime();
            for (int i = 0; i < tasksAwaiting.size(); i++) {
                if (tasksAwaiting.get(i).id.equals(ev.getTaskId())) {
                    AppInfo t = tasksAwaiting.remove(i);
                    long timeTaken = endTick - t.timeSent;
                    averageLatency = (averageLatency * noResults + timeTaken) / (++noResults);
                    cltInfoLog(EVENT_TASK_CONCLUDED, "taskId=" + ev.getTaskId() + " finTime=" + ev.getTickConcluded() + "handlerId=" + ev.getHandlerId());
                    return;
                }
            }
            System.out.print("Error this should nto happen task ended without having id in awaiting tasks.");

        }
    }

    public long selectNextTime() {
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

    public void clientInit(){
        tasksAwaiting = new ArrayList<>();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public double getAverageTaskSize() {
        return averageTaskSize;
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

    protected void printParams() {
        //if(active)
        cltDbgLog("DAGClient Params: CPI<" + Arrays.toString(this.CPI) + "> T<" + Arrays.toString(this.BYTE_SIZE) + "> I<" + Arrays.toString(this.NO_INSTR) + ">");
    }

    @Override
    public double getAverageByteSize() {
        return averageByteSize;
    }

    @Override
    public String toString() {
        return (active) ? "DAGClient{" +

                " tasksAwaiting=" + tasksAwaiting.size() +
                ", averageLatency=" + averageLatency +
                ", noResults=" + noResults +
                ", tick=" + CommonState.getTime() +
                ", id=" + id +
                '}'
                : "DAGClient{inactive}";
    }

    public void cltInfoLog(String event, String info) {
        Log.logInfo("CLT", this.id, event, info);

    }

    public void cltDbgLog(String msg) {
        Log.logDbg("CLT", this.id, "DEBUG", msg);
    }

    public void cltErrLog(String msg) {
        Log.logErr("CLT", this.id, "ERROR", msg);
    }

    private class AppInfo {

        protected String id;
        protected long timeSent;
        protected double deadline;

        public AppInfo(String id, long timeSent, double deadline) {
            this.id = id;
            this.timeSent = timeSent;
        }

    }
}