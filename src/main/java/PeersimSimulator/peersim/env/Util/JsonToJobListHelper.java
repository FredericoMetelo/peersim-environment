package PeersimSimulator.peersim.env.Util;

import PeersimSimulator.peersim.env.Records.AlibabaClusterJob;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonToJobListHelper {

    public static List<AlibabaClusterJob> readJsonToJobList(String filePath){
        try {
            File jsonFile = new File(filePath);

            // Check if the file exists
            if (!jsonFile.exists()) {
                System.out.println("File not found: " + filePath);
                return null;
            }

            ObjectMapper objectMapper = new ObjectMapper();

            // Read JSON file into a Map<String, Job>
            Map<String, Map<String, Double>> jobMap = objectMapper.readValue(jsonFile, Map.class);

            // Convert the Map values to a List of Job objects
            List<AlibabaClusterJob> jobList = jobMap.values().stream().map(
                    job -> new AlibabaClusterJob(job.get("critical_path_resources_cpu"),
                            job.get("critical_path_resources_mem"),
                            job.get("total_resources_cpu"),
                            job.get("total_resources_mem"),
                            job.get("total_resources_instances"),
                            job.get("total_resources_duration"),
                            job.get("critical_path_duration"),
                            job.get("max_mem"),
                            job.get("max_cpu")
                    )
            ).toList();

            // Print the list of Job objects
//            for (AlibabaClusterJob job : jobList) {
//                System.out.println(job);
//            }
            return jobList;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
    public static void main(String[] args) {
        // Testing only.
        String filePath = "/home/fm/IdeaProjects/peersim-environment/Datasets/alibaba_trace_cleaned.json";
        List<AlibabaClusterJob> a = readJsonToJobList(filePath);
        for (AlibabaClusterJob job : a) {
            System.out.println(job);
        }

    }
}
