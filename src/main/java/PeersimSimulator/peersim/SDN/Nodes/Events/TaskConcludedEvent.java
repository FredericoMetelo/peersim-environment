package PeersimSimulator.peersim.SDN.Nodes.Events;

public class TaskConcludedEvent implements Message {
    int tickConcluded;

    int nodeId;

    String taskId;

    byte[] result;

    public TaskConcludedEvent(int nodeId, String taskId) {
        this.nodeId = nodeId;
        this.taskId = taskId;
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

    public byte[] getResult() {
        return result;
    }

    public void setResult(byte[] result) {
        this.result = result;
    }

    @Override
    public double getSize() {
        return 1;
    }
}
