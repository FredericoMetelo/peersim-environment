package PeersimSimulator.peersim.env.Records;

import java.util.List;

public record DebugInfo(int selectedNode,

                        long currentCycle,
                        List<Integer> droppedTotal,
                        List<Integer> droppedThisCycle,
                        List<Integer> totalTasksRecieved,
                        List<Integer> tasksRecievedSinceLastCycle,
                        List<Integer> totalTasksProcessed,
                        List<Integer> totalTasksOffloaded,
                        List<Boolean> invariantPreserved,
                        List<Double> averageResponseTime)
{

}
