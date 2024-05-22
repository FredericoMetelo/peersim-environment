package PeersimSimulator.peersim.env.Nodes.Events;

import PeersimSimulator.peersim.env.Nodes.Clients.Client;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.env.Records.Coordinates;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkerInfo implements Message{

    /**
     * This is a constant value representing a default expected task size based. Represents the knowledge the node has
     * of a priori expected task size.
     */
    // private static final double DEFAULT_TASK_SIZE = Client.DEFAULT_TASK_SIZE * Client.DEFAULT_NO_INSTR;
    @JsonProperty("id")
    private int id; // the index of the node.


    /**
     * Number of Tasks in nodes queue.
     */
    @JsonProperty("queueSize")
    private int queueSize;

    /**
     * Number of Requests that arrived at this node and are awaiting processing.
     */
    @JsonProperty("unprocessedApplications")
    private int unprocessedApplications;
    @JsonProperty("averageTaskSize")
    private double averageTaskSize;
    @JsonProperty("nodeProcessingPower")
    private double nodeProcessingPower;
    @JsonProperty("freeTaskSlots")
    private int freeTaskSlots;

    @JsonProperty("lastKnownPosition")
    private Coordinates lastKnownPosition;

    @JsonProperty("layer")
    private int layer;


    @JsonCreator
    public WorkerInfo(@JsonProperty("id") int id,
                      @JsonProperty("queueSize") int queueSize,
                      @JsonProperty("unprocessedApplications") int unprocessedApplications,
                      @JsonProperty("averageTaskSize") double averageTaskSize,
                      @JsonProperty("nodeProcessingPower") double nodeProcessingPower,
                      @JsonProperty("freeTaskSlots") int freeTaskSlots,
                      @JsonProperty("layer") int layer,
                      @JsonProperty("lastKnownPosition") Coordinates lastKnownPosition){
        this.id = id;
        this.queueSize = queueSize;
        this.unprocessedApplications = unprocessedApplications;
        this.averageTaskSize = averageTaskSize;
        this.nodeProcessingPower = nodeProcessingPower;
        this.freeTaskSlots = freeTaskSlots;
        this.layer = layer;
        this.lastKnownPosition = lastKnownPosition;
    }

    public int getTotalTasks() {
        return this.getQueueSize() + getUnprocessedApplications();
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     *
     * @return the expected amount of tasks to be processed in that node in a unit of time.
     */
    public double getW() {
        Client clt = (Client) Network.get(0).getProtocol(Client.getPid());
        return (averageTaskSize == 0) ? nodeProcessingPower/(clt.getAverageTaskSize()) : nodeProcessingPower/(averageTaskSize);
    }


    public Coordinates getLastKnownPosition() {
        return lastKnownPosition;
    }

    public void setLastKnownPosition(Coordinates lastKnownPosition) {
        this.lastKnownPosition = lastKnownPosition;
    }

    public void setUnprocessedApplications(int unprocessedApplications) {
        this.unprocessedApplications = unprocessedApplications;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUnprocessedApplications() {
        return unprocessedApplications;
    }

    public double getAverageTaskSize() {
        return averageTaskSize;
    }

    public void setAverageTaskSize(double averageTaskSize) {
        this.averageTaskSize = averageTaskSize;
    }

    public double getNodeProcessingPower() {
        return nodeProcessingPower;
    }

    public void setNodeProcessingPower(double nodeProcessingPower) {
        this.nodeProcessingPower = nodeProcessingPower;
    }

    public int getFreeTaskSlots() {
        return freeTaskSlots;
    }

    public void setFreeTaskSlots(int freeTaskSlots) {
        this.freeTaskSlots = freeTaskSlots;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    @Override
    public String toString() {
        return "<WI - ID:" + this.id + " Q:"+this.getTotalTasks()+">";
    }

    @Override
    public double getSize() {
        // 52 bytes, obtained from adding the space used by the variables.
        return 52;
    }
}
