package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.env.Tasks.ITask;

import java.util.List;

public record SimulationData(int srcId, double distance, WorkerInfo wi, List<ITask> tasksCompleted) {
}
