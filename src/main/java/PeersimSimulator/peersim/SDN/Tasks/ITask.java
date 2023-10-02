package PeersimSimulator.peersim.SDN.Tasks;

import java.util.UUID;

public abstract class ITask {
    /**
     * Unique ID used to identify the task.
     */
    protected final String id;

    /**
     * Stores the node id that requested the task.
     */
    protected int originNodeId;

    /**
     * Store the node id that is processing the task.
     * Exists for debugging purposes mostly.
     */
    protected int processingNodeId;
    /**
     * Size of the data being sent. Bytes in body of task request.
     */
    protected final double sizeBytes;

    protected double progress;

    /**
     * Total Amount of instructions needed to execute task.
     *w
     * I should note that this value would usually be tied to the type of request being done and would be an estimated
     * average value.
     */
    protected final double totalInstructions;

    public ITask(double sizeBytes, double totalInstructions, int originNodeId, int processingNodeId) {
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

    public boolean done(){
        return this.progress == this.totalInstructions;
    }

    public abstract double addProgress(double cycles);

}
