package PeersimSimulator.peersim.SDN.Nodes.Events;

public class WorkerInfo {

    private int id; // the index of the node.


    /**
     * Number of Tasks in nodes queue.
     */
    private int queueSize;

    /**
     * Number of Requests that arrived at this node and are awaiting processing.
     */
    private int W;

    public WorkerInfo(int id, int queueSize, int w) {
        this.id = id;
        this.queueSize = queueSize;
        this.W = w;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getW() {
        return W;
    }

    public void setW(int w) {
        W = w;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
