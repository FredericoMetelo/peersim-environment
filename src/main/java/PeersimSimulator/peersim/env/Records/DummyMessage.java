package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Nodes.Events.Message;

public class DummyMessage implements Message {
    private final double size;

    public DummyMessage(double size) {
        this.size = size;
    }

    @Override
    public double getSize() {
        return size;
    }
}
