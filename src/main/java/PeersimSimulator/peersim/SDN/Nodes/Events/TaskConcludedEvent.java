package PeersimSimulator.peersim.SDN.Nodes.Events;

public class TaskConcludedEvent implements Message{

    private final double outputSize;
    int handlerID;
    String appID;

    int clientID;

    public TaskConcludedEvent(int handlerID, String appID, int clientID, double outputSize) {
        this.handlerID = handlerID;
        this.appID = appID;
        this.clientID = clientID;
        this.outputSize = outputSize;
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

    @Override
    public double getSize() {
        return outputSize;
    }
}
