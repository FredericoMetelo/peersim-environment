package PeersimSimulator.peersim.SDN.Nodes.Events;

public class AppConcludedEvent implements Message {
    int tickConcluded;

    int nodeId;

    String taskId;

    double outputSize;

    public AppConcludedEvent(int nodeId, String taskId, double outputSize) {
        this.nodeId = nodeId;
        this.taskId = taskId;
        this.outputSize = outputSize;
    }

    public int getTickConcluded() {
        return tickConcluded;
    }

    public void setTickConcluded(int tickConcluded) {
        this.tickConcluded = tickConcluded;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public double getSize() {
        return outputSize;
    }
}
