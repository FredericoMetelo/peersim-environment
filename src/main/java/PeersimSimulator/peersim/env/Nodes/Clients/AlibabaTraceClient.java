package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.env.Tasks.Application;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class AlibabaTraceClient  extends AbstractClient {
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



    }

    @Override
    public Application generateApplication(int target) {
        return null;
    }

    private void readCSV(String csvFile) {
        // Read the .csv file and populate the arrays with the data.
        // ChatGPT generated the following code:
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Split the line into an array of values
                String[] values = line.split(",");

                // Process each value as needed
                for (String value : values) {
                    System.out.print(value + " ");
                }

                System.out.println(); // Move to the next line for the next CSV row
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
