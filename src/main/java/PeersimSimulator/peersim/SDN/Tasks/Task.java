package PeersimSimulator.peersim.SDN.Tasks;

import java.util.UUID;

public class Task {
    /**
     * Unique ID used to identify the task.
     */
    private final String id;

    /**
     * Stores the node id that requested the task.
     */
    private int originNodeId;

    /**
     * Store the node id that is processing the task.
     * Exists for debugging purposes mostly.
     */
    private int processingNodeId;
    /**
     * Size of the data being sent. Bytes in body of task request.
     */
    private final double sizeBytes;

    private double progress;
    /**
     * Total Amount of instructions needed to execute task.
     *
     * I should note that this value would usually be tied to the type of request being done and would be an estimated
     * average value.
     */
    private final double totalInstructions;

    public Task(double sizeBytes, double totalInstructions, int originNodeId, int processingNodeId) {
        this.id = UUID.randomUUID().toString();
        this.sizeBytes = sizeBytes;
        this.totalInstructions = totalInstructions;
        this.originNodeId = originNodeId;
        this.processingNodeId = processingNodeId;
    }



    public String getId() {
        return id;
    }

    public double getSizeBytes() {
        return sizeBytes;
    }

    public int getProcessingNodeId() {
        return processingNodeId;
    }

    public void setProcessingNodeId(int processingNodeId) {
        this.processingNodeId = processingNodeId;
    }

    public double getTotalInstructions() {
        return totalInstructions;
    }

    public int getOriginNodeId() {
        return originNodeId;
    }
    public void setOriginNodeId(int id) {
        this.originNodeId = id;
    }
    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
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
    public boolean done(){
        return this.progress == this.totalInstructions;
    }
}
