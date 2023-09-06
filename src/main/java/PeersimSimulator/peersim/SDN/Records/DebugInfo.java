package PeersimSimulator.peersim.SDN.Records;

import java.util.List;

public record DebugInfo(int selectedNode,

                        long currentCycle,
                        List droppedTotal,
                        List droppedThisCycle,
                        List<Integer> totalTasksRecieved,
                        List<Integer> tasksRecievedSinceLastCycle,
                        List<Integer> totalTasksProcessed,
                        List<Integer> totalTasksOffloaded,
                        List<Boolean> invariantPreserved)
{

}
