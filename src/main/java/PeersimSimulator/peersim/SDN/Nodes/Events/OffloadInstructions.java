package PeersimSimulator.peersim.SDN.Nodes.Events;

public class OffloadInstructions implements Message{
    private int neighbourIndex;

    public OffloadInstructions(int neighbourIndex) {
        this.neighbourIndex = neighbourIndex;

    }

    public int getNeighbourIndex() {
        return neighbourIndex;
    }

    public void setNeighbourIndex(int neighbourIndex) {
        this.neighbourIndex = neighbourIndex;
    }

    @Override
    public double getSize() {
        return 1; // 16 bytes which is two ints is 0.0016 Mbytes
    }

    @Override
    public String toString() {
        return "<OI - " +
                "neighbourIndex=" + neighbourIndex +
                '>';
    }
}
