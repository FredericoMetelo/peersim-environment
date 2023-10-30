package PeersimSimulator.peersim.SDN.Records;

public record Action (int neighbourIndex, int controllerId) {
    public boolean isAwait(){
        return neighbourIndex == -1 || controllerId == -1;
    }

    @Override
    public String toString() {
        return "target: " + neighbourIndex + " no_task: " + controllerId;
    }
}
