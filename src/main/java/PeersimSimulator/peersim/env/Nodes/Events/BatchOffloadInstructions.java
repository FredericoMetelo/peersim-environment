package PeersimSimulator.peersim.env.Nodes.Events;

import java.util.List;

public class BatchOffloadInstructions implements Message, OffloadInstructions {

    private List<Integer> neighbourIndexes;


    public BatchOffloadInstructions(List<Integer> neighbourIndexes) {
        this.neighbourIndexes = neighbourIndexes;
    }

    public List<Integer> neighbourIndexes() {
        return neighbourIndexes;
    }


    @Override
    public double getSize() {
        return 0;
    }
}
