package PeersimSimulator.peersim.env.Records;

import java.util.List;

public record PartialState(
        int nodeId,
        List<Integer> Q,
        double processingPower,
        double averageWaitingTime,
        int layer,
        Coordinates position,
        double bandwidth,
        double transmissionPower
) {

}
