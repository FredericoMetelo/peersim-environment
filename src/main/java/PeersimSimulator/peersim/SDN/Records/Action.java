package PeersimSimulator.peersim.SDN.Records;

public record Action (int nodeId, int noTasks) {
    public boolean isAwait(){
        return nodeId == -1 || noTasks == -1;
    }
}
