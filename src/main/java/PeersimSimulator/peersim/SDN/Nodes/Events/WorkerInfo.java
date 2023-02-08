package PeersimSimulator.peersim.SDN.Nodes.Events;

import PeersimSimulator.peersim.SDN.Nodes.Client;

import java.util.List;

public class WorkerInfo {

    private int id; // the index of the node.


    /**
     * Number of Tasks in nodes queue.
     */
    private int queueSize;

    /**
     * Number of Requests that arrived at this node and are awaiting processing.
     */
    private int W_i;

    private double averageTaskSize;

    private double nodeProcessingPower;




    public WorkerInfo(int id, int queueSize, int w, double averageTaskSize, double nodeProcessingPower) {
        this.id = id;
        this.queueSize = queueSize;
        this.W_i = w;
        this.averageTaskSize = averageTaskSize;
        this.nodeProcessingPower = nodeProcessingPower;
    }

    public int getQueueSize() {
        return queueSize + getW_i();
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     *
     * @return the expected amount of tasks to be processed in that node in a unit of time.
     */
    public double getW() {
        return (averageTaskSize == 0) ? 0 : nodeProcessingPower/(averageTaskSize * Client.CPI);
    }

    public void setW_i(int w_i) {
        W_i = w_i;
    }

    public int getW_i() {
        return W_i;
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
}
