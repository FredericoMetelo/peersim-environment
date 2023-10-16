package PeersimSimulator.peersim.SDN.Records;

import PeersimSimulator.peersim.SDN.Tasks.ITask;

import java.util.Comparator;

public class DependentTaskComparator implements Comparator<ITask> {
    @Override
    public int compare(ITask application, ITask t1) {
        if(application.getId().equals(t1.getId())) return 0;
        // Must guarantee that 0 is never returned... Otherwise insertion does not happen!!!!s
        int rankComparisson = Double.compare(application.getCurrentRank(), t1.getCurrentRank());

        return (rankComparisson != 0) ? rankComparisson : -1; // They can't be euqal therefore if they are considered equal, the one already there is considered to be smaller.
    }
}
