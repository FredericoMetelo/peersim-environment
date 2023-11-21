package PeersimSimulator.peersim.env.Records;

import java.util.List;

public record State(List<PartialState> observedState, GlobalState globalState) {
}
