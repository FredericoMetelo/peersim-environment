package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.env.Records.AlibabaClusterJob;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.Task;
import PeersimSimulator.peersim.env.Util.JsonToJobListHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class AlibabaTraceClient  extends AbstractClient {


    private static final String DEFAULT_CPU_WORKLOAD = "defaultCPUWorkload";
    private double defaultCPUWorkload;
    private static final String DEFAULT_MEMORY_WORKLOAD = "defaultMemoryWorkload";
    private double defaultMemoryWorkload;
    private static final String WORKLOAD_PATH = "workloadPath";

    private List<AlibabaClusterJob> applicationCandidates;

    private String workloadPath;
    public AlibabaTraceClient(String prefix) {
        super(prefix);
    }

    @Override
    protected void getTaskMetadata(String prefix) {
        /*
        Here I need to read the .csv file with the data needed to create the applications. I will basically create a bunch
        of entries in the CPI, BYTE_SIZE, NO_INSTR, and TASK_WEIGHTS arrays. Based on the data in the .csv file. This
        way I have to change a lot less code to make the simulator work with the Alibaba trace.
        */

        workloadPath = Configuration.getString(prefix + "." + WORKLOAD_PATH);
        defaultCPUWorkload = Configuration.getDouble(prefix + "." + DEFAULT_CPU_WORKLOAD, 2.4e+9); // AMD EPYC™ 9654 and Raspberry 5 frequencies
        defaultMemoryWorkload = Configuration.getDouble(prefix + "." + DEFAULT_MEMORY_WORKLOAD, 100);
        List<AlibabaClusterJob> jobList = JsonToJobListHelper.readJsonToJobList(workloadPath);
        this.applicationCandidates = jobList;
        assert jobList != null;
        numberOfTasks = jobList.size();

        taskCumulativeProbs = new double[numberOfTasks];

        averageTaskSize = 0;
        averageByteSize = 0;
        assert jobList != null;
        int i = 0;
        for(AlibabaClusterJob job: jobList){
            // Need to think about how to convert the data from the weird units used in the
            averageTaskSize += getCPUFromJob(job);
            averageByteSize += getMemoryFromJob(job);
            taskCumulativeProbs[i++] = 1.0 / jobList.size();
        }
        averageTaskSize /= jobList.size();
        averageByteSize /= jobList.size();
    }

    @Override
    public Application generateApplication(int target) {
        Map<String, ITask> tasks = new HashMap<>();
        Map<String, String> taskIDToVertice = new HashMap<>();
        Map<String, String> verticesToTaskID = new HashMap<>();
        String appID = UUID.randomUUID().toString();
        AlibabaClusterJob job = pickJob();
        ITask firstTask = new Task(getMemoryFromJob(job), getMemoryFromJob(job), getCPUFromJob(job), this.getId(), target, appID, "0");
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

    private AlibabaClusterJob pickJob() {
        Random random = new Random();
        int index = random.nextInt(applicationCandidates.size());
        return applicationCandidates.get(index);
    }

    private double getCPUFromJob(AlibabaClusterJob job){
        // TODO Define the unit for this value
        /*
               requested CPU: every 100 unit means 1 core.
               we assumed the time was in milliseconds, so we need to divide by 1000 to get seconds
         */
        return job.getMaxCPU()/100 * job.getTotalResourcesDuration()/1000 * defaultCPUWorkload;
    }
    private double getMemoryFromJob(AlibabaClusterJob job) {
        // TODO Define the unit for this value
        /*
                requested memory: every 100 unit we will consider 1GB
         */
        return job.getMaxMemory() * defaultMemoryWorkload;
    }

}
