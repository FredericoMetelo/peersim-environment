package PeersimSimulator.peersim.SDN.Records;

import PeersimSimulator.peersim.SDN.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.SDN.Tasks.ITask;

import java.util.List;

public record SimulationData(int srcId, double distance, WorkerInfo wi) {
}
