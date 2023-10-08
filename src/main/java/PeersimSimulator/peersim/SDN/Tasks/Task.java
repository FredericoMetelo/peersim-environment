package PeersimSimulator.peersim.SDN.Tasks;

import java.util.UUID;

public class Task extends ITask{

    public Task(double inputSizeBytes, int outputSizeBytes, double totalInstructions, int clientID, int originalHandlerID, String applicationID) {
        super(inputSizeBytes, outputSizeBytes, totalInstructions, clientID, originalHandlerID, applicationID);
    }

    /**
     * Executes the task. Then informs the user of how many cycles are left from the provided, if no cycles left returns 0.
     * @param cycles number of free cycles that can be used.
     * @return the number of cycles left from the given number fo cycles.
     */
    public double addProgress(double cycles){
        double total_cycles = this.progress + cycles;
        this.progress = Math.min(total_cycles, this.totalInstructions);
        return Math.max(0, total_cycles - this.totalInstructions);
    }
}
