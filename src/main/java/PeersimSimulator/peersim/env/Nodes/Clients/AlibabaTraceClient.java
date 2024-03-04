package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.env.Records.AlibabaClusterJob;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Util.JsonToJobListHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AlibabaTraceClient  extends AbstractClient {

    private static final String WORKLOAD_PATH = "workloadPath";
    private String workloadPath;
    public AlibabaTraceClient(String prefix) {
        super(prefix);
        workloadPath = Configuration.getString(WORKLOAD_PATH);

    }

    @Override
    protected void getTaskMetadata(String prefix) {
        /*
        Here I need to read the .csv file with the data needed to create the applications. I will basically create a bunch
        of entries in the CPI, BYTE_SIZE, NO_INSTR, and TASK_WEIGHTS arrays. Based on the data in the .csv file. This
        way I have to change a lot less code to make the simulator work with the Alibaba trace.
        */
        List<AlibabaClusterJob> jobList = JsonToJobListHelper.readJsonToJobList(workloadPath);


    }

    @Override
    public Application generateApplication(int target) {
        return null;
    }

    private void readJSONToDict(String path) {
        try {
            File jsonFile = new File(path);
            ObjectMapper objectMapper = new ObjectMapper();
            Map jsonMap = objectMapper.readValue(jsonFile, Map.class);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
