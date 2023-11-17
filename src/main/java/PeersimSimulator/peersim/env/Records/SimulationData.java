package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;

public record SimulationData(int srcId, double distance, WorkerInfo wi) {
}
