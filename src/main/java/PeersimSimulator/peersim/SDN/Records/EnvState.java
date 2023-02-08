package PeersimSimulator.peersim.SDN.Records;

import PeersimSimulator.peersim.SDN.Nodes.Events.WorkerInfo;

import java.util.List;

public record EnvState (int n_i, List<Integer> Q, double w) {

}
