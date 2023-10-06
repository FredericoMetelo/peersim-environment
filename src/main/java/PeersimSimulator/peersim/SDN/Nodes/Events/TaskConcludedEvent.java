package PeersimSimulator.peersim.SDN.Nodes.Events;

public class TaskConcludedEvent implements Message{

    int handlerID;
    String appID;

    int clientID;

    public TaskConcludedEvent(int handlerID, String appID, int clientID) {
        this.handlerID = handlerID;
        this.appID = appID;
        this.clientID = clientID;
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
        return 0;
    }
}
