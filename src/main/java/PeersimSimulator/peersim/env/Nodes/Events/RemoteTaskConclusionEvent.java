package PeersimSimulator.peersim.env.Nodes.Events;

public class RemoteTaskConclusionEvent implements Message{

    String appId;
    String subTaskID;

    int handlerID;

    int processingNodeID;

    public RemoteTaskConclusionEvent(String appId, String subTaskID, int handlerID, int processingNodeID) {

        this.appId = appId;
        this.subTaskID = subTaskID;
        this.handlerID = handlerID;
        this.processingNodeID = processingNodeID;
    }

    public String getAppId() {
        return appId;
    }

    public String getSubTaskID() {
        return subTaskID;
    }

    public int getHandlerID() {
        return handlerID;
    }

    public int getProcessingNodeID() {
        return processingNodeID;
    }

    @Override
    public double getSize() {
        return 0;
    }
}
