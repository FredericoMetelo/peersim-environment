package PeersimSimulator.peersim.env.Nodes.Clients;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.Task;

import java.util.*;

public class ClassClient  extends AbstractClient {


    private static final String PAR_CLIENT_CLASSES = "clientClasses";

    private int clientClass;

    public ClassClient(String prefix) {
        super(prefix);
    }
    @Override
    protected void getTaskMetadata(String prefix) { // Bad name: maybe initeTaskMetadata
        String[] _CPI = Configuration.getString(prefix + "." + PAR_CPI, "1").split(",");
        String[] _BYTE_SIZE = Configuration.getString(prefix + "." + PAR_BYTE_SIZE, DEFAULT_TASK_SIZE).split(",");
        String[] _NO_INSTR = Configuration.getString(prefix + "." + PAR_NO_INSTR, DEFAULT_NO_INSTR).split(",");


        CPI = Arrays.stream(_CPI).mapToInt(num -> Integer.parseInt(num)).toArray();
        BYTE_SIZE = Arrays.stream(_BYTE_SIZE).mapToDouble(num -> Double.parseDouble(num)).toArray();
        NO_INSTR = Arrays.stream(_NO_INSTR).mapToDouble(num -> Double.parseDouble(num)).toArray();
        String[] _TASK_WEIGHTS_STR = Configuration.getString(prefix + "." + PAR_TASKS_WEIGHTS, DEFAULT_NO_INSTR).split(",");

        // Get probabilities of each task. Might have problems if to big differences in weight beween tasks.
        double[] _WEIGHTS = Arrays.stream(_TASK_WEIGHTS_STR).mapToDouble(num -> Double.parseDouble(num)).toArray();
        double total_weight = Arrays.stream(_WEIGHTS).sum();
        double[] _TASK_WEIGHTS = Arrays.stream(_WEIGHTS).map(num -> num / total_weight).toArray();


        // Aquire the full list of clients and task types. Exclusively generate tasks from that class.
        String[] clientClasses = Configuration.getString(prefix + "." + PAR_CLIENT_CLASSES).split(";");
        int[] _clientClasses = Arrays.stream(clientClasses).mapToInt(num -> Integer.parseInt(num)).toArray();

        // test if enough classes are defined
        if (_clientClasses.length <= this.id) { // Should blow eventually if not enough classes, but not the most correct.
            throw new RuntimeException("Not enough client classes defined in the configuration for ClassClient with id " + this.id);
        }

        this.clientClass = _clientClasses[this.id]; // I have accidentally big brained this. And I have all nodes distributed in classes. Not just clt. So this just works.

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
    }

    @Override
    public Application generateApplication(int target) {

        Map<String, ITask> tasks = new HashMap<>();
        Map<String, String> taskIDToVertice = new HashMap<>();
        Map<String, String> verticesToTaskID = new HashMap<>();
        String appID = UUID.randomUUID().toString();

        int taskType = this.clientClass;
        ITask firstTask = new Task(BYTE_SIZE[taskType], BYTE_SIZE[taskType], NO_INSTR[taskType] * CPI[taskType], this.getId(), target, appID, "0");
        tasks.put(firstTask.getId(), firstTask);
        verticesToTaskID.put("0", firstTask.getId());
        taskIDToVertice.put(firstTask.getId(), "0");

        ITask lastTask = firstTask;

        Map<String, List<ITask>> successors = new HashMap<>();
        Map<String, List<ITask>> predecessors = new HashMap<>();

        predecessors.put(firstTask.getId(), new ArrayList<>());
        successors.put(firstTask.getId(), new ArrayList<>());

        double deadline = CommonState.getTime() + CommonState.r.nextInt(minDeadline, minDeadline * 2);
        List<ITask> list = new ArrayList<>();
        list.add(firstTask);

        Application app = new Application(tasks, predecessors, successors, deadline, appID, this.getId(), firstTask.getInputSizeBytes(), lastTask.getOutputSizeBytes(), firstTask, list);
        return app;
    }
}
