package PeersimSimulator.peersim.env.Nodes.Events;

import PeersimSimulator.peersim.env.Tasks.ITask;

public class TaskConcludedEvent implements Message{

    private final double outputSize;
    int handlerID;
    String appID;

    int clientID;
    ITask task;

    public TaskConcludedEvent(int handlerID, String appID, int clientID, double outputSize, ITask task) {
        this.handlerID = handlerID;
        this.appID = appID;
        this.clientID = clientID;
        this.outputSize = outputSize;
        this.task = task;
    }

    public int getHandlerID() {
        return handlerID;
    }

    public String getAppID() {
        return appID;
    }

    public int getClientID() {
        return clientID;
    }

    public double getOutputSize() {
        return outputSize;
    }

    public ITask getTask() {
        return task;
    }

    @Override
    public double getSize() {
        return outputSize;
    }
}
