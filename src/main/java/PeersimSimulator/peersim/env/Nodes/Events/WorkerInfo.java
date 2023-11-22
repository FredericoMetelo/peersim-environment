package PeersimSimulator.peersim.env.Nodes.Events;

import PeersimSimulator.peersim.env.Nodes.Client;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.env.Records.Coordinates;

public class WorkerInfo implements Message{

    /**
     * This is a constant value representing a default expected task size based. Represents the knowledge the node has
     * of a priori expected task size.
     */
    // private static final double DEFAULT_TASK_SIZE = Client.DEFAULT_TASK_SIZE * Client.DEFAULT_NO_INSTR;
    private int id; // the index of the node.


    /**
     * Number of Tasks in nodes queue.
     */
    private int queueSize;

    /**
     * Number of Requests that arrived at this node and are awaiting processing.
     */
    private int unprocessedApplications;

    private double averageTaskSize;

    private double nodeProcessingPower;

    private int freeTaskSlots;

    private Coordinates lastKnownPosition;


    private int layer;

    public WorkerInfo(int id, int queueSize, int w, double averageTaskSize, double nodeProcessingPower, int freeTaskSlots, int layer, Coordinates lastKnownPosition) {
        this.id = id;
        this.queueSize = queueSize;
        this.unprocessedApplications = w;
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

    public int getUnprocessedApplications() {
        return unprocessedApplications;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public double getAverageWaitingTime(){
        return averageTaskSize/nodeProcessingPower;
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
        return 1;
    }
}
