package PeersimSimulator.peersim.SDN.Records;

import PeersimSimulator.peersim.SDN.Tasks.ITask;

import java.util.Comparator;

public class DependentTaskComparator implements Comparator<ITask> {
    @Override
    public int compare(ITask application, ITask t1) {
        return Double.compare(application.getCurrentRank(), t1.getCurrentRank());
    }
}
