package PeersimSimulator.peersim.SDN.Nodes.Events;

public class OffloadInstructions {
    private int targetNode;
    private int noTasks;

    public OffloadInstructions(int targetNode, int noTasks) {
        this.targetNode = targetNode;
        this.noTasks = noTasks;
    }

    public int getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(int targetNode) {
        this.targetNode = targetNode;
    }

    public int getNoTasks() {
        return noTasks;
    }

    public void setNoTasks(int noTasks) {
        this.noTasks = noTasks;
    }
}
