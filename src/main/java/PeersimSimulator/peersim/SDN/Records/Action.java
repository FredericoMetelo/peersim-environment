package PeersimSimulator.peersim.SDN.Records;

public record Action (int neighbourIndex, int noTasks) {
    public boolean isAwait(){
        return neighbourIndex == -1 || noTasks == -1;
    }

    @Override
    public String toString() {
        return "target: " + neighbourIndex + " no_task: " + noTasks;
    }
}
