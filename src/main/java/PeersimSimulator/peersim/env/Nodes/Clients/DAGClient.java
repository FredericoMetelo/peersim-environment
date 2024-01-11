package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.env.Nodes.Workers.WorkerInitializer;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.Task;

import java.util.*;
import java.util.stream.Collectors;

public class DAGClient extends AbstractClient {

    // PARAMETERS =========================================================================================
    protected static final String PAR_NUMBER_DAG = "numberOfDAG";
    protected static final String PAR_DAG_WEIGHTS = "dagWeights";

    private static final String PAR_EDGES = "edges";
    private static final String PAR_VERTICES = "vertices";

    // VARIABLES =========================================================================================
    protected final int[] layers;
    protected final int numberOfTasks;
    protected final int numberOfDAG;
    protected final double TASK_ARRIVAL_RATE;



    private List<Map<String, List<String>>> successorsPerDAGType;
    private List<Map<String, List<String>>> predecessorsPerDAGType;
    private List<List<String>> expandedDAGs;
    private List<Integer> numberOfVerticesPerDAGType;

    public DAGClient(String prefix) {
        super(prefix); // TODO queue size isn't doing much...
        pid = Configuration.getPid(prefix + "."+PAR_NAME ); //

        // Read Constants
        minDeadline = Configuration.getInt(prefix + "." + PAR_MIN_DEADLINE, DEFAULT_MAX_DEADLINE);
        TASK_ARRIVAL_RATE = Configuration.getDouble( prefix + "." + PAR_TASKARRIVALRATE, DEFAULT_TASKARRIVALRATE );
        numberOfTasks = Configuration.getInt( prefix + "." + PAR_NO_TASKS, DEFAULT_NUMBEROFTASKS);
        numberOfDAG = Configuration.getInt( prefix + "." + PAR_NUMBER_DAG, DEFAULT_NUMBEROFDAG);
        String[] _layers = Configuration.getString(WorkerInitializer.PAR_NO_NODES_PER_LAYERS).split(",");
        layers = Arrays.stream(_layers).mapToInt(Integer::parseInt).toArray();


        getTaskMetadata(prefix);
        computeApplicationData(prefix);

        printParams();

    }

    @Override
    public void getTaskMetadata(String prefix){
        String[] _CPI = Configuration.getString(prefix + "." + PAR_CPI, "1").split(",");
        String[] _BYTE_SIZE = Configuration.getString(prefix + "." + PAR_BYTE_SIZE, DEFAULT_TASK_SIZE).split(",");
        String[] _NO_INSTR = Configuration.getString(prefix + "." + PAR_NO_INSTR, DEFAULT_NO_INSTR).split(",");

        String[] _TASK_WEIGHTS_STR = Configuration.getString(prefix + "." + PAR_TASKS_WEIGHTS, DEFAULT_NO_INSTR).split(",");
        String[] _DAG_WEIGHTS_STR = Configuration.getString(prefix + "." + PAR_DAG_WEIGHTS, DEFAULT_NO_INSTR).split(";");
        if (_CPI.length != numberOfTasks || _NO_INSTR.length != numberOfTasks || _BYTE_SIZE.length != numberOfTasks
                || _TASK_WEIGHTS_STR.length != numberOfTasks || _DAG_WEIGHTS_STR.length != numberOfDAG) {
            throw new RuntimeException("The number of tasks and the number of parameters for the tasks do not match");
        }

        CPI = Arrays.stream(_CPI).mapToInt(num -> Integer.parseInt(num)).toArray();
        BYTE_SIZE = Arrays.stream(_BYTE_SIZE).mapToDouble(num -> Double.parseDouble(num)).toArray();
        NO_INSTR = Arrays.stream(_NO_INSTR).mapToDouble(num -> Double.parseDouble(num)).toArray();

        // Get probabilities of each task. Might have problems if to big differences in weight beween tasks.
        double[] _WEIGHTS = Arrays.stream(_TASK_WEIGHTS_STR).mapToDouble(num -> Double.parseDouble(num)).toArray();
        double total_weight = Arrays.stream(_WEIGHTS).sum();
        double[] _TASK_WEIGHTS = Arrays.stream(_WEIGHTS).map(num -> num / total_weight).toArray();

        double[] _WEIGHTS_DAG = Arrays.stream(_DAG_WEIGHTS_STR).mapToDouble(num -> Double.parseDouble(num)).toArray();
        double total_weight_DAG = Arrays.stream(_WEIGHTS_DAG).sum();
        double[] _DAG_WEIGHTS = Arrays.stream(_WEIGHTS_DAG).map(num -> num / total_weight_DAG).toArray();


        taskCumulativeProbs = new double[numberOfTasks];

        // First element, there must always be at least one task type.
        averageTaskSize = _TASK_WEIGHTS[0] * CPI[0] * NO_INSTR[0];
        averageByteSize = _TASK_WEIGHTS[0] * BYTE_SIZE[0];
        taskCumulativeProbs[0] = _TASK_WEIGHTS[0];

        for (int i = 1; i < numberOfTasks; i++) {
            // Compute the average task size and the weight of each task
            averageTaskSize += _TASK_WEIGHTS[i] * CPI[i] * NO_INSTR[i];
            averageByteSize += _TASK_WEIGHTS[0] * BYTE_SIZE[i];
            taskCumulativeProbs[i] = taskCumulativeProbs[i - 1] + _TASK_WEIGHTS[i];
        }
        dagCumulativeProbs = new double[numberOfDAG];
        System.arraycopy(_DAG_WEIGHTS, 0, dagCumulativeProbs, 0, numberOfDAG);
    }

    protected void computeApplicationData(String prefix) {

        this.successorsPerDAGType = new ArrayList<>(numberOfDAG);
        this.predecessorsPerDAGType = new ArrayList<>(numberOfDAG);
        this.numberOfVerticesPerDAGType = new ArrayList<>(numberOfDAG);
        this.expandedDAGs = new ArrayList<>(numberOfDAG);

        String[] edgeTypes = Configuration.getString(prefix + "." + PAR_EDGES, "").split(";");
        String[] vertices = Configuration.getString(prefix + "." + PAR_VERTICES, "1").split(";");
        if (edgeTypes.length != numberOfDAG || numberOfDAG != vertices.length) {
            cltErrLog("Wrong configs, number of DAGs does not have parity of edge and vertice types -> vertice types: " + vertices.length + " edge types: " + edgeTypes.length);
            throw new RuntimeException("Wrong configs, number of DAGs does not have parity of edge and vertice types -> vertice types: " + vertices.length + " edge types: " + edgeTypes.length);
        }
        for (int i = 0; i < edgeTypes.length; i++) {
            String[] edges = edgeTypes[i].split(",");
            if (edges[0].isEmpty()) {
                List<String> expandedDag = this.expandToList(new HashMap<>(), new HashMap<>(), Integer.parseInt(vertices[i]));
                this.expandedDAGs.add(expandedDag);
                this.predecessorsPerDAGType.add(new HashMap<>());
                this.successorsPerDAGType.add(new HashMap<>());
                this.numberOfVerticesPerDAGType.add(Integer.parseInt(vertices[i]));
                continue;
            }
            // Aka an entry is < predecessor, successor[] >
            Map<String, List<String>> successors = new HashMap<>();
            //Aka an entry is < successor, predecessor[] >
            Map<String, List<String>> predecessors = new HashMap<>();
            int lastVertice = Integer.parseInt(vertices[i]);
            for (String edge : edges) {
                String[] e = edge.split("->");
                String predecessor = e[0];
                String successor = e[1];
                int pred = Integer.parseInt(predecessor);
                int succ = Integer.parseInt(successor);
                if (pred < 0 || succ < 0 || pred >= lastVertice || succ >= lastVertice)
                    throw new RuntimeException("There are illegal vertices in the edges of dag type: " + i + " last vertice id:" + lastVertice);
                // TODO: Test for cycles? Do so on the environment side?
                addToMap(successors, predecessor, successor);
                addToMap(predecessors, successor, predecessor);
            }
            List<String> expandedDag = this.expandToList(new HashMap<>(successors), new HashMap<>(predecessors), lastVertice);

            this.expandedDAGs.add(expandedDag);
            this.successorsPerDAGType.add(successors);
            this.predecessorsPerDAGType.add(predecessors);
            this.numberOfVerticesPerDAGType.add(Integer.parseInt(vertices[i])); // This might throw exceptions: ¯\_(ツ)_/¯

        }
    }

    private void addToMap(Map<String, List<String>> map, String key, String value) {
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
    protected int pickDAGType() {
        double aux = CommonState.r.nextDouble(0, numberOfDAG);
        return AbstractClient.findFirstBigger(dagCumulativeProbs, aux);
    }
    @Override
    public Application generateApplication(int target) {
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
        for (int i = 1; i < noTasks; i++) {
            taskType = this.pickTaskType(); // For convenience, I'll consider the output size the same as the input size
            ITask task = new Task(BYTE_SIZE[taskType], BYTE_SIZE[taskType], NO_INSTR[taskType] * CPI[taskType], this.getId(), target, appID, Integer.toString(i));
            verticesToTaskID.put(Integer.toString(i), task.getId());
            taskIDToVertice.put(task.getId(), Integer.toString(i));

            tasks.put(task.getId(), task);
            if (i == noTasks) {
                lastTask = task;
            }
        }
        // TODO this must have a better way of solving... This pains me to do... But I do what I must.
        Map<String, List<ITask>> successors = new HashMap<>();
        Map<String, List<ITask>> predecessors = new HashMap<>();
        for (String t : tasks.keySet()) {
            String vertice = taskIDToVertice.get(t); // There is a better way to do this now that I added a vertice property to the task
            if (predecessorIDs.containsKey(vertice)) {
                List<ITask> pred = predecessorIDs.get(vertice).stream()
                        .map(verticesToTaskID::get) // Convert list of vertices into list of taskID
                        // .filter(tasks::containsKey) redundant? // Remove from the list the taskIDs
                        .map(tasks::get)
                        .collect(Collectors.toList());
                predecessors.put(t, pred);
            } else {
                predecessors.put(t, new ArrayList<>(0));
            }
            if (successorsIDs.containsKey(vertice)) {
                List<ITask> succ = successorsIDs.get(vertice).stream()
                        .map(verticesToTaskID::get) // Convert list of vertices into list of taskID
                        // .filter(tasks::containsKey) redundant? // Remove from the list the taskIDs
                        .map(tasks::get)
                        .collect(Collectors.toList());
                successors.put(t, succ);
            } else {
                successors.put(t, new ArrayList<>(0));
            }
        }

        // TODO Convert this to computing the minimum deadline required for finishing the task.
        double deadline = CommonState.getTime() + CommonState.r.nextInt(minDeadline, minDeadline * 2);
        List<ITask> list = expandedDAGs.get(dagType).stream().map(it -> tasks.get(verticesToTaskID.get(it))).toList();
        return new Application(tasks, predecessors, successors, deadline, appID, this.getId(), firstTask.getInputSizeBytes(), lastTask.getOutputSizeBytes(), firstTask, list);
    }

    /**
     * Kahn's Algorithm for expanding an App
     */
    public List<String> expandToList(Map<String, List<String>> successors, Map<String, List<String>> predecessors, int size) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        // Initialize the queue with vertex 0 (the root with no predecessors)
        queue.offer("0");
        visited.add("0");

        while (!queue.isEmpty()) {
            String vertex = queue.poll();
            result.add(vertex);

            List<String> adjacentVertices = successors.get(vertex);
            if (adjacentVertices != null) {
                for (String successor : adjacentVertices) {
                    // Remove the current vertex from the predecessors of the successor.
                    predecessors.get(successor).remove(vertex);
                    if (predecessors.get(successor).isEmpty() && !visited.contains(successor)) {
                        // If by removing the vertice all predecessors are met we add it to result, because by now all it's dependencies have been met.
                        queue.offer(successor);
                        visited.add(successor);
                    }
                }
            }
        }

        if (result.size() != size) {
            // Graph contains a cycle
            throw new IllegalArgumentException("The graph has a cycle.");
        }

        return result;

    }
}
