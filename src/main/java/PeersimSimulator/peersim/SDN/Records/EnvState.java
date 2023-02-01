package PeersimSimulator.peersim.SDN.Records;

import PeersimSimulator.peersim.SDN.Nodes.Events.WorkerInfo;

import java.util.List;

public record EnvState (List<WorkerInfo> wi, int n_i, int w_i) {

}
