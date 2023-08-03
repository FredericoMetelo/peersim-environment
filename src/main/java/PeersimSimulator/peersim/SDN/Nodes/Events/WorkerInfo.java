package PeersimSimulator.peersim.SDN.Nodes.Events;

import PeersimSimulator.peersim.SDN.Nodes.Client;
import PeersimSimulator.peersim.core.Network;

public class WorkerInfo {

    /**
     * This is a constant value representing a default expected task size based. Represents the knowledge the node has
     * of a priori expected task size.
     */
    private static final double DEFAULT_TASK_SIZE = Client.DEFAULT_TASK_SIZE * Client.DEFAULT_NO_INSTR;
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

    public int getTotalTasks() {
        return this.getQueueSize() + getW_i();
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
        return (averageTaskSize == 0) ? nodeProcessingPower/(DEFAULT_TASK_SIZE) : nodeProcessingPower/(averageTaskSize * clt.CPI);
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

    @Override
    public String toString() {
        return "<WorkerInfo - ID:" + this.id + " Q:"+this.getTotalTasks()+">";
    }
}
