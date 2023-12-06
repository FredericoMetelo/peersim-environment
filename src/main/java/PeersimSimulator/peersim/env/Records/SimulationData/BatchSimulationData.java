package PeersimSimulator.peersim.env.Records.SimulationData;

import PeersimSimulator.peersim.env.Tasks.ITask;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;



public class BatchSimulationData extends SimulationData{

    private List<Double> distances;
    // private final List<WorkerInfo> wi;
    private List<ITask> tasksCompleted;

    @JsonCreator
    public BatchSimulationData(@JsonProperty("srcId") int srcId,
                               @JsonProperty("distances") List<Double> distances,
                               @JsonProperty("tasksCompleted") List<ITask> tasksCompleted) {
        super(srcId);
        this.distances = distances;
        this.tasksCompleted = tasksCompleted;
    }

    public List<Double> getDistances() {
        return distances;
    }

    public void setDistances(List<Double> distances) {
        this.distances = distances;
    }

    public List<ITask> getTasksCompleted() {
        return tasksCompleted;
    }

    public void setTasksCompleted(List<ITask> tasksCompleted) {
        this.tasksCompleted = tasksCompleted;
    }
}
