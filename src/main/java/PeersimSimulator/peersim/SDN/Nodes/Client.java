package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Nodes.Events.NewApplicationEvent;
import PeersimSimulator.peersim.SDN.Tasks.Application;
import PeersimSimulator.peersim.SDN.Tasks.ITask;
import PeersimSimulator.peersim.SDN.Tasks.Task;
import PeersimSimulator.peersim.SDN.Util.Log;
import PeersimSimulator.peersim.SDN.Nodes.Events.AppConcludedEvent;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.transport.Transport;

import java.util.*;
import java.util.stream.Collectors;

public class Client implements CDProtocol, EDProtocol {
    public static final String DEFAULT_TASK_SIZE = "500";
    public static final String DEFAULT_NO_INSTR = "200e6";
    public static final double DEFAULT_TASKARRIVALRATE = 0.01;

    public static final int DEFAULT_NUMBEROFTASKS = 1;
    public static final int DEFAULT_NUMBEROFDAG = 1;

    private static final String PAR_BYTE_SIZE = "T";
    public static final int DEFAULT_MAX_DEADLINE = 50;
    private double[] BYTE_SIZE; // Mbytes
    private double averageByteSize;


    private static final String PAR_NO_INSTR = "I";

    public double[] NO_INSTR;
    private static final String PAR_TASKS_WEIGHTS = "weight";
    private double[] taskCumulativeProbs;


    private static final String PAR_MAX_DEADLINE = "maxDeadline";
    private int maxDeadline;

    private static final String PAR_CPI = "CPI";
    public int[] CPI;

    private static final String PAR_NO_TASKS = "numberOfTasks";
    private final int numberOfTasks;


    private static final String PAR_NUMBER_DAG = "numberOfDAG";

    private final int numberOfDAG;

    private static final String PAR_DAG_WEIGHTS = "dagWeights";

    private double[] dagCumulativeProbs;


    private static final String PAR_EDGES = "edges";
    private List<Map<String, List<String>>> successorsPerDAGType;
    private List<Map<String, List<String>>> predecessorsPerDAGType;


    private static final String PAR_VERTICES = "vertices";
    private List<Integer> numberOfVerticesPerDAGType;



    private static final String PAR_NAME = "name";
    private static int pid;

    public static String PAR_TASKARRIVALRATE = "taskArrivalRate";
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
    List<AppInfo> tasksAwaiting;


    private float averageLatency;
    private int noResults;

    private List<Long> nextArrival;

    public double averageTaskSize;

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
        maxDeadline = Configuration.getInt(prefix + "." +PAR_MAX_DEADLINE, DEFAULT_MAX_DEADLINE);
        TASK_ARRIVAL_RATE = Configuration.getDouble( prefix + "." + PAR_TASKARRIVALRATE, DEFAULT_TASKARRIVALRATE );
        numberOfTasks = Configuration.getInt( prefix + "." + PAR_NO_TASKS, DEFAULT_NUMBEROFTASKS);
        numberOfDAG = Configuration.getInt( prefix + "." + PAR_NO_TASKS, DEFAULT_NUMBEROFDAG);



        getTaskMetadata(prefix);
        computeApplicationData(prefix);

        printParams();

    }

    private void getTaskMetadata(String prefix){ // Bad name: maybe initeTaskMetadata
        String[] _CPI = Configuration.getString( prefix + "." + PAR_CPI, "1").split(",") ;
        String[] _BYTE_SIZE = Configuration.getString( prefix + "." + PAR_BYTE_SIZE, DEFAULT_TASK_SIZE).split(",") ;
        String[] _NO_INSTR = Configuration.getString( prefix + "." + PAR_NO_INSTR, DEFAULT_NO_INSTR).split(",") ;

        String[] _TASK_WEIGHTS_STR = Configuration.getString( prefix + "." + PAR_TASKS_WEIGHTS, DEFAULT_NO_INSTR).split(",") ;
        String[] _DAG_WEIGHTS_STR =  Configuration.getString( prefix + "." + PAR_DAG_WEIGHTS, DEFAULT_NO_INSTR).split(";") ;
        if(_CPI.length != numberOfTasks || _NO_INSTR.length != numberOfTasks || _BYTE_SIZE.length != numberOfTasks
                || _TASK_WEIGHTS_STR.length != numberOfTasks || _DAG_WEIGHTS_STR.length != numberOfDAG){
             throw new RuntimeException("The number of tasks and the number of parameters for the tasks do not match");
        }

        CPI = Arrays.stream(_CPI).mapToInt(num -> Integer.parseInt(num)).toArray();
        BYTE_SIZE = Arrays.stream(_BYTE_SIZE).mapToDouble(num -> Double.parseDouble(num)).toArray();
        NO_INSTR = Arrays.stream(_NO_INSTR).mapToDouble(num -> Double.parseDouble(num)).toArray();

        // Get probabilities of each task. Might have problems if to big differences in weight beween tasks.
        double[] _WEIGHTS = Arrays.stream(_TASK_WEIGHTS_STR).mapToDouble(num -> Double.parseDouble(num)).toArray();
        double total_weight = Arrays.stream(_WEIGHTS).sum();
        double[] _TASK_WEIGHTS = Arrays.stream(_WEIGHTS).map(num -> num/total_weight).toArray();

        double[] _WEIGHTS_DAG = Arrays.stream(_DAG_WEIGHTS_STR).mapToDouble(num -> Double.parseDouble(num)).toArray();
        double total_weight_DAG = Arrays.stream(_WEIGHTS_DAG).sum();
        double[] _DAG_WEIGHTS = Arrays.stream(_WEIGHTS_DAG).map(num -> num/total_weight_DAG).toArray();


        taskCumulativeProbs = new double[numberOfTasks];

        // First element, there must always be at least one task type.
        averageTaskSize = _TASK_WEIGHTS[0] * CPI[0] * NO_INSTR[0];
        averageByteSize = _TASK_WEIGHTS[0] * BYTE_SIZE[0];
        taskCumulativeProbs[0] = _TASK_WEIGHTS[0];

        for(int i = 1; i < numberOfTasks; i++){
            // Compute the average task size and the weight of each task
            averageTaskSize += _TASK_WEIGHTS[i] * CPI[i] * NO_INSTR[i];
            averageByteSize += _TASK_WEIGHTS[0] * BYTE_SIZE[i];
            taskCumulativeProbs[i] = taskCumulativeProbs[i-1] + _TASK_WEIGHTS[i];
        }
        dagCumulativeProbs = new double[numberOfDAG];
        System.arraycopy(_DAG_WEIGHTS, 0, dagCumulativeProbs, 0, numberOfDAG);

    }


    private void computeApplicationData(String prefix){

        this.successorsPerDAGType = new ArrayList<>(numberOfDAG);
        this.predecessorsPerDAGType = new ArrayList<>(numberOfDAG);
        this.numberOfVerticesPerDAGType = new ArrayList<>(numberOfDAG);


        String[] edgeTypes = Configuration.getString(prefix + "." + PAR_EDGES, "").split(";");
        String[] vertices = Configuration.getString(prefix + "." + PAR_VERTICES, "1").split(";");
        if(edgeTypes.length != numberOfDAG && numberOfDAG != vertices.length) {
            Log.err("Wrong configs, number of DAGs does not have parity of edge and vertice types -> vertice types: " + vertices.length+ " edge types: " + edgeTypes.length);
            return;
        }
        for (int i = 0; i < edgeTypes.length; i++) {
            String[] edges = edgeTypes[i].split(",");
            // Aka an entry is < predecessor, successor[] >
            Map<String, List<String>> successors = new HashMap<>();
            //Aka an entry is < successor, predecessor[] >
            Map<String, List<String>> predecessors = new HashMap<>();
            for (String edge : edges) {
                String[] e = edge.split(" ");
                String predecessor = e[0];
                String successor = e[1];
                addToMap(successors, predecessor, successor);
                addToMap(predecessors, successor, predecessor);
            }
            this.successorsPerDAGType.add(successors);
            this.predecessorsPerDAGType.add(predecessors);
            this.numberOfVerticesPerDAGType.add(Integer.parseInt(vertices[i])); // This might throw exceptions: ¯\_(ツ)_/¯
        }
    }

    private void addToMap(Map<String, List<String>> map, String key, String value){
         if (map.containsKey(key)) {
             // If it exists, add the value to the existing list
             map.get(key).add(value);
         } else {
             // If it doesn't exist, create a new list and add the value to it
             List<String> list = new ArrayList<>();
             list.add(value);
             map.put(key, list);
         }
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

        for(int i = 0; i < linkable.degree(); i++) {
            if (nextArrival.get(i) <= CommonState.getTime()) {
                Node target = linkable.getNeighbor(i);
                if (!target.isUp()) return; // This happens task progress is lost.



                Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
                Application app = generateApplication(i); // new Task(BYTE_SIZE[taskType], NO_INSTR[taskType] * CPI[taskType], this.getId(), wi.getId()); // TODO this needs to be changed to something that will properly acommodate whatever I need to implement.
                tasksAwaiting.add(new AppInfo(app.getAppID(), CommonState.getTime(), app.getDeadline()));
                Log.info("|CLT| TASK SENT to Node:<" + wi.getId() + "> FROM < " + this.getId()+">");
                ((Transport) target.getProtocol(FastConfig.getTransport(Worker.getPid()))).
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

    /**
     * IMPORTANT FOR THE CONFIGS!!!!!
     *  Task 0 must never have predecessors (aka, be the first task),
     *  the last index task must not have any successors (aka be the last task)
     * @param target
     * @return
     */
    public Application generateApplication(int target){
        //TODO
        int dagType = this.pickDAGType();

        Map<String, List<String>> predecessorIDs = this.predecessorsPerDAGType.get(dagType);
        Map<String, List<String>> successorsIDs = this.successorsPerDAGType.get(dagType);
        int noTasks = this.numberOfVerticesPerDAGType.get(dagType);

        Map<String, ITask> tasks = new HashMap<>();
        Map<String, String> taskIDToVertice = new HashMap<>();
        Map<String, String> verticesToTaskID = new HashMap<>();
        String appID = UUID.randomUUID().toString();

        int taskType = this.pickTaskType();
        ITask firstTask = new Task(BYTE_SIZE[taskType], BYTE_SIZE[taskType], NO_INSTR[taskType] * CPI[taskType], this.getId(), target, appID, "0");
        tasks.put(firstTask.getId(), firstTask);
        verticesToTaskID.put("0", firstTask.getId());
        taskIDToVertice.put(firstTask.getId(), "0");

        ITask lastTask = firstTask;
        for (int i = 1; i <= noTasks; i++) {
            taskType = this.pickTaskType(); // For convenience, I'll consider the output size the same as the input size
            ITask task = new Task(BYTE_SIZE[taskType], BYTE_SIZE[taskType], NO_INSTR[taskType] * CPI[taskType], this.getId(), target, appID, Integer.toString(i));
            verticesToTaskID.put(Integer.toString(i), task.getId());
            taskIDToVertice.put(task.getId(), Integer.toString(i));

            tasks.put(task.getId(), task);
            if(i == noTasks){
                lastTask = task;
            }
        }
        // TODO this must have a better way of solving... This pains me to do... But I do what I must.
        Map<String, List<ITask>> successors = new HashMap<>();
        Map<String, List<ITask>> predecessors = new HashMap<>();
        for(String t : tasks.keySet()){
            String vertice = taskIDToVertice.get(t); // There is a better way to do this now that I added a vertice property to the task
            if(predecessorIDs.containsKey(vertice)) {
                List<ITask> pred = predecessorIDs.get(vertice).stream()
                        .map(verticesToTaskID::get) // Convert list of vertices into list of taskID
                        // .filter(tasks::containsKey) redundant? // Remove from the list the taskIDs
                        .map(tasks::get)
                        .collect(Collectors.toList());
                predecessors.put(t, pred);
            }else{
                predecessors.put(t, new ArrayList<>(0));
            }
            if(successorsIDs.containsKey(vertice)){
                List<ITask> succ = successorsIDs.get(vertice).stream()
                        .map(verticesToTaskID::get) // Convert list of vertices into list of taskID
                        // .filter(tasks::containsKey) redundant? // Remove from the list the taskIDs
                        .map(tasks::get)
                        .collect(Collectors.toList());
                successors.put(t, succ);
            }else{
                successors.put(t, new ArrayList<>(0));
            }
        }

        // TODO Convert this to computing the minimum deadline required for finishing the task.
        double deadline = CommonState.getTime() + CommonState.r.nextInt(maxDeadline, maxDeadline*2 );

        return new Application(tasks, predecessors, successors, deadline, appID, this.getId(), firstTask.getInputSizeBytes(), lastTask.getOutputSizeBytes(), firstTask);

    }
    private int pickTaskType(){ // Btw variable is a randDouble not a randInt
        double aux = CommonState.r.nextDouble(0, numberOfTasks - 1);
        return findFirstBigger(taskCumulativeProbs, aux);
    }
    private int pickDAGType(){
        double aux = CommonState.r.nextDouble(0, numberOfDAG - 1);
        return findFirstBigger(dagCumulativeProbs, aux);
    }

    @Override
    public void processEvent(Node node, int pid, Object event) {
        if(!active) return;
        // Recieve answer from task sent.
        if(event instanceof AppConcludedEvent ev){
             long endTick = CommonState.getTime();
            for(int i = 0; i < tasksAwaiting.size(); i++){
                if(tasksAwaiting.get(i).id.equals(ev.getTaskId())){
                    AppInfo t = tasksAwaiting.remove(i);
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

    public double getAverageTaskSize() {
        return averageTaskSize;
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

    private class AppInfo {
        protected String id;
        protected long timeSent;

        protected double deadline;

        public AppInfo(String id, long timeSent, double deadline) {
            this.id = id;
            this.timeSent = timeSent;
        }
    }

    private void printParams(){
        //if(active)
            Log.dbg("Client Params: CPI<" + this.CPI + "> T<" + this.BYTE_SIZE+ "> I<"+ this.NO_INSTR+">" );
    }

    public double getAverageByteSize() {
        return averageByteSize;
    }

    /**
     * Binary search for the index of the first bigger value. This is ChatGPT generated but I've confirmed by hand it
     * works as intended.
     * @param sortedArray
     * @param target
     * @return the index of the first bigger value.
     */
    private static int findFirstBigger(double[] sortedArray, double target) {
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
}
