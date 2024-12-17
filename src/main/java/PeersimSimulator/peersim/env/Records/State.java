package PeersimSimulator.peersim.env.Records;

import java.util.List;

public record State(GlobalState globalState, List<PartialState> observedState) {
}
