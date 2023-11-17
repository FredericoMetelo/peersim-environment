package PeersimSimulator.peersim.env.Nodes.Events;

public class AppConcludedEvent implements Message {
    int tickConcluded;

    int handlerId;

    String taskId;

    double outputSize;

    public AppConcludedEvent(int handlerId, String taskId, double outputSize) {
        this.handlerId = handlerId;
        this.taskId = taskId;
        this.outputSize = outputSize;
    }

    public int getTickConcluded() {
        return tickConcluded;
    }

    public void setTickConcluded(int tickConcluded) {
        this.tickConcluded = tickConcluded;
    }

    public int getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(int handlerId) {
        this.handlerId = handlerId;
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
