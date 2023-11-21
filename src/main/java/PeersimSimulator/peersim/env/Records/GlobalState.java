package PeersimSimulator.peersim.env.Records;

import java.util.List;

public record GlobalState(
        List<Integer> nodeIds,
        List<Integer> Q,
        List<Double> processingPowers,
        List<Integer> noCores,
        List<Integer> layers,
        List<Coordinates> positions,
        List<Double> bandwidths,
        List<Double> transmissionPowers
) {
}
