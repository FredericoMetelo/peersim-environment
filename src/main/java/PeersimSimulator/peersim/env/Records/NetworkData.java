package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;

import java.util.List;

public record NetworkData(int min, int max, int average, List<List<Integer>> neighbourMatrix, List<List<Integer>> knowsControllerMatrix) {
}
