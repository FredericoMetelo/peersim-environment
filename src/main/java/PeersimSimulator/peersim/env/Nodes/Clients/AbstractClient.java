package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.Simulator;
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
    private static final String PAR_CLIENT_IS_SELF = "clientIsSelf";
    private final int clientIsSelf;

    protected double[] BYTE_SIZE; // Mbytes
    protected double averageByteSize;
    protected double[] NO_INSTR;
    protected double[] taskCumulativeProbs;
    protected int minDeadline;
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
    protected List<Integer> amountToGenerate;


    protected double averageTaskSize;

    protected final int[] layers;
    protected int numberOfTasks;
    protected Schedule schedule;

    protected double taskArrivalRate;

    private final int[] layersThatGetTasks;

    protected int scale;

    // 0.10 % chance of new task being sent to worker per time tick.
    // Assuming poisson process therefore no change!
    protected int id;
    /**
     * List with Ids of the tasks awaiting response.
     */
    List<AppInfo> tasksAwaiting;
    /**
     * Measures the tasks that are travelling.
     * This is a metadata list that is used to keep track of the tasks that are travelling. A task is travelling when it
     * moving through the network, wither by an offload or task generation to another worker or cloud.
     */
    int tasksOnClientTravelling;

    private int tasksCompleted;
    private int totalTasks;
    private int droppedTasks;
    
    public AbstractClient(String prefix) {
        pid = Configuration.getPid(prefix + "."+PAR_NAME ); //

        // Read Constants
        minDeadline = Configuration.getInt(prefix + "." + PAR_MIN_DEADLINE, DEFAULT_MAX_DEADLINE);
        minDeadline = (minDeadline <= 0) ? Integer.MAX_VALUE : minDeadline;

        taskArrivalRate = Configuration.getDouble( prefix + "." + PAR_TASKARRIVALRATE, DEFAULT_TASKARRIVALRATE );
        numberOfTasks = Configuration.getInt( prefix + "." + PAR_NO_TASKS, DEFAULT_NUMBEROFTASKS);
        String[] _layers = Configuration.getString(WorkerInitializer.PAR_NO_NODES_PER_LAYERS).split(",");
        layers = Arrays.stream(_layers).mapToInt(Integer::parseInt).toArray();

        String[] _layersThatGetTasks = Configuration.getString(PAR_LAYERS_THAT_GET_TASKS).split(",");
        layersThatGetTasks = Arrays.stream(_layersThatGetTasks).mapToInt(Integer::parseInt).toArray();

        scale = Configuration.getInt(PAR_SCALE, 1);
        minDeadline = minDeadline * scale; // Scale the deadline to the time scale of the simulation.

        clientIsSelf = Configuration.getInt(PAR_CLIENT_IS_SELF, 0);
        tasksCompleted = 0;
        droppedTasks = 0;
        totalTasks = 0;

        getTaskMetadata(prefix);
    }

    /**
     * Binary search for the index of the first bigger value.
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

    /**
     * This method should extract the required information to generate tasks for the given implementation of the client.
     * @param prefix
     */
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
        if(this.clientIsSelf == 1 && nextArrival.get(0) <= CommonState.getTime()
                    && canGetTasks(((Worker) linkable.getNeighbor(0).getProtocol(Worker.getPid())).getLayer())){
            sendTaskAndDecideNextEventTime(node, linkable, 0); // Send to self which is always on position 0.
        }else{
            for (int i = 0; i < linkable.degree(); i++) {
                if (nextArrival.get(i) <= CommonState.getTime()
                        && canGetTasks(((Worker) linkable.getNeighbor(i).getProtocol(Worker.getPid())).getLayer())) { // Not sure of the legality of this...
                    sendTaskAndDecideNextEventTime(node, linkable, i);
                }
            }
        }
        droppedTasks += cleanUpTasks();
        // Note: This is not the best way to do this. It's O(n), n is the number of tasksAwaiting. A better solution
        // might be to clean up dropped tasks whenever a task completes. Because tasksAwaiting is a list, the older tasks
        // will be at the start of the list and the newer ones at the end. So by cleaning up the start of the list we
        // will get the benefits of making the tasksAwaiting list smaller and also get the droppedTasks count.
        double oldTA = taskArrivalRate;
        taskArrivalRate = schedule.getCurrentTaskArrivalRate();
        Long curr = nextArrival.get(0);
        Long next = Math.round(CommonState.getTime() + selectNextTime());
        // Reschedule the next task arrival. If the current task arrival would have the task arrive sooner than the current arrival time;
        if (oldTA != taskArrivalRate && next < curr) {
            nextArrival.set(0, next);
        }
    }

    private void sendTaskAndDecideNextEventTime(Node node, Linkable linkable, int i) {
        Node target = linkable.getNeighbor(i);
        if (!target.isUp()) return;
        Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
        while(this.amountToGenerate.get(i) > 0){
            Application app = generateApplication((int) target.getID());
            tasksAwaiting.add(new AppInfo(app.getAppID(), CommonState.getTime(), app.getDeadline()));
            totalTasks++;
            cltInfoLog(EVENT_TASK_SENT, "target=" + wi.getId());
            ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            target,
                            new NewApplicationEvent(app.getAppID(), this.id, app.getInitialDataSize(), app),
                            Worker.getPid()
                    );
            this.amountToGenerate.set(i, this.amountToGenerate.get(i) - 1);
        }
        double time = selectNextTime();
        amountToGenerate.set(i, 1); // (int) Math.max(1/time, 1)); -- Reverting to one task only, getting ridiculous for tASK_ARRIVAL_RATE > 1
        nextArrival.set(i, Math.round(CommonState.getTime() + time));
    }

    private int cleanUpTasks() {
        int dropped = 0;
        long now = CommonState.getTime();
        Iterator<AppInfo> it = tasksAwaiting.iterator();
        while (it.hasNext()) {
            AppInfo t = it.next();
            if (t.timeSent + t.deadline < now) {
                it.remove();
                dropped++;
            }
        }
        return dropped;
    }

    protected int pickTaskType() { // Btw variable is a randDouble not a randInt
        double aux = CommonState.r.nextDouble(0, numberOfTasks);
        return AbstractClient.findFirstBigger(taskCumulativeProbs, aux);
    }


//    /**
//     * Adds the task to the travelling queue.
//     */
//    public  void setAppNotTravelling(){
//        tasksOnClientTravelling++;
//        assert tasksOnClientTravelling >= 0;
//    }
//
//    /**
//     * Removes the task from the travelling queue.
//     */
//    public void setAppTravelling(){
//        tasksOnClientTravelling--;
//        assert tasksOnClientTravelling >= 0;
//    }


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
                    tasksCompleted++;
                    cltInfoLog(EVENT_TASK_CONCLUDED, "taskId=" + ev.getTaskId() + " finTime=" + ev.getTickConcluded() + "handlerId=" + ev.getHandlerId());
                    return;
                }
            }
            System.out.print("Error this should nto happen task ended without having id in awaiting tasks.");
        }
    }

    public double selectNextTime() {
        /*
            Based on the formula from: https://preshing.com/20111007/how-to-generate-random-timings-for-a-poisson-process/
            From CDF of exponential function we have: 1 - e^{-lambda * x}. This is the probability no events occur on the
             first x time units. Then we invert that to go from probability to time and sample a probability with uniform
             RV.

             Note: The task arrival rate of this poisson process is lambda.
         */
        // If Im expecting more tasks then time between them must be smaller! Capped at 1 task per time-step though.
        double time = (-Math.log(CommonState.r.nextDouble()) / (taskArrivalRate /scale)) ;
        return time;
    }

    private void initTaskManagement(int degree) {
        if (schedule == null)
            throw new RuntimeException("Schedule is null. This should not happen.");
        if(!schedule.isStarted())
            schedule.start();

        nextArrival = new ArrayList<Long>(degree);
        amountToGenerate = new ArrayList<>(degree);
        for (int i = 0; i < degree; i++) {
            double time = selectNextTime();
            this.amountToGenerate.add(1); // .add((int) Math.max(1/time, 1));
            nextArrival.add(Math.round(time));
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
        cltDbgLog("Client Params: CPI<" + Arrays.toString(this.CPI) + "> T<" + Arrays.toString(this.BYTE_SIZE) + "> I<" + Arrays.toString(this.NO_INSTR) + ">");
    }


    @Override
    public double getAverageByteSize() {
        return averageByteSize;
    }

    public double getAverageTaskCompletionTime() {
        return averageLatency;
    }

    private boolean canGetTasks(int layer) {
        return Arrays.stream(layersThatGetTasks).anyMatch(i -> i == layer);
    }

    @Override
    public String toString() {
        return (active) ? "Client("+this.getId()+"){" +

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

    @Override
    public int getTasksCompleted() {
        return tasksCompleted;
    }

    @Override
    public int getTotalTasks() {
        return totalTasks;
    }

    @Override
    public int getDroppedTasks() {
        return droppedTasks;
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
            this.deadline = deadline;
        }

    }

    @Override
    public void setSchedules(String schedule) {
        this.schedule = new Schedule(schedule); // Started inside the constructor.
        this.taskArrivalRate = this.schedule.getCurrentTaskArrivalRate();

    }

    private class Schedule{
        Queue<ScheduleEntry> scheduleEntries;
        ScheduleEntry currentEntry;
        long timeInEntry;

        public Schedule(String schedule){
            scheduleEntries = new LinkedList<>();
            currentEntry = null;
            timeInEntry = 0;
            String[] entries = schedule.split(",");
            for (String entry : entries) {
                String[] entryData = entry.split("-");
                addEntry(Double.parseDouble(entryData[0]), Integer.parseInt(entryData[1])*scale);
            }
        }

        public Schedule() {
            scheduleEntries = new LinkedList<>();
            currentEntry = null;
            timeInEntry = 0;
        }

        private void addEntry(double taskArrivalRate, int time){
            scheduleEntries.add(new ScheduleEntry(taskArrivalRate, time));
            if (!isStarted()){
                start();
            }
        }
        public void start(){
            currentEntry = scheduleEntries.poll();
            timeInEntry = 0;
        }
        public boolean isStarted(){
            return currentEntry != null;
        }
        public double getCurrentTaskArrivalRate(){
            /**
             * Returns the current task arrival rate given the time. This function should be called every time step.
             * If the time in the current entry is greater than the time in the entry, then the current entry is updated.
             */
//            timeInEntry++;
            long currTime = CommonState.getTime();
            if (currTime >= currentEntry.time + timeInEntry && !scheduleEntries.isEmpty()){
                currentEntry = next();
                timeInEntry = CommonState.getTime();
                cltInfoLog("SCHEDULE_CHANGE", "client="+ id +" time=" + currTime + " newRate=" + currentEntry.taskArrivalRate);
            }
            return currentEntry.taskArrivalRate;
        }

        public int getCurrentTime(){
            return currentEntry.time;
        }

        private boolean hasNext(){
            return currentEntry != null;
        }

        private ScheduleEntry next(){
            return scheduleEntries.poll();
        }


    }
    private class ScheduleEntry{
        double taskArrivalRate;
        int time;

        public ScheduleEntry(double taskArrivalRate, int time) {
            this.taskArrivalRate = taskArrivalRate;
            this.time = time;
        }

        @Override
        public String toString() {
            return "ScheduleEntry{" +
                    "taskArrivalRate=" + taskArrivalRate +
                    ", time=" + time +
                    '}';
        }
    }
}
