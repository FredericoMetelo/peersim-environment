package PeersimSimulator.peersim.SDN.Nodes.Events;

public class OffloadInstructions implements Message{
    private int targetNode;

    public OffloadInstructions(int targetNode) {
        this.targetNode = targetNode;

    }

    public int getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(int targetNode) {
        this.targetNode = targetNode;
    }

    @Override
    public double getSize() {
        return 1; // 16 bytes which is two ints is 0.0016 Mbytes
    }
}
