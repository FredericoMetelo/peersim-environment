package PeersimSimulator.peersim.env.Tasks;

import java.util.UUID;

public abstract class ITask {
    /**
     * Unique ID used to identify the task.
     */
    protected final String id;

    /**
     * Stores the node id that requested the task.
     */
    protected int clientID;

    /**
     * Store the node id that is processing the task.
     * Exists for debugging purposes mostly.
     */
    protected int originalHandlerID;

    protected String applicationID;

    /**
     * Size of the data being sent. Bytes in body of task request.
     */
    protected final double inputSizeBytes;

    protected final double outputSizeBytes;

    protected double progress;

    private String vertice;

    private double currentRank;
    /**
     * Total Amount of instructions needed to execute task.
     *w
     * I should note that this value would usually be tied to the type of request being done and would be an estimated
     * average value.
     */
    protected final double totalInstructions;

    public ITask(double inputSizeBytes, double outputSizeBytes, double totalInstructions, int clientID, int originalHandlerID, String applicationID, String vertice) {
        this.id = UUID.randomUUID().toString();
        this.inputSizeBytes = inputSizeBytes;
        this.outputSizeBytes = outputSizeBytes;
        this.totalInstructions = totalInstructions;
        this.clientID = clientID;
        this.originalHandlerID = originalHandlerID;
        this.applicationID = applicationID;
        this.currentRank = -1;
        this.vertice = vertice;
    }

    public double getOutputSizeBytes() {
        return outputSizeBytes;
    }

    public String getId() {
        return id;
    }

    public String getAppID(){
        return this.applicationID;
    }

    public double getInputSizeBytes() {
        return inputSizeBytes;
    }

    public int getOriginalHandlerID() {
        return originalHandlerID;
    }

    public void setOriginalHandlerID(int originalHandlerID) {
        this.originalHandlerID = originalHandlerID;
    }

    public double getTotalInstructions() {
        return totalInstructions;
    }

    public int getClientID() {
        return clientID;
    }
    public void setClientID(int id) {
        this.clientID = id;
    }

    public double getProgress() {
        return progress;
    }

    public boolean done(){
        return this.progress == this.totalInstructions;
    }

    public double getCurrentRank() {
        return currentRank;
    }

    public void setCurrentRank(double currentRank) {
        this.currentRank = currentRank;
    }

    public abstract double addProgress(double cycles);

    @Override
    public String toString() {
        return "ITask{" +
                "id='" + id + '\'' +
                ", vertice='" + vertice + '\'' +
                ", clientID=" + clientID +
                ", currentRank=" + currentRank +
                ", originalHandlerID=" + originalHandlerID +
                ", applicationID='" + applicationID + '\'' +
                ", progress=" + progress +
                '}';
    }
}
