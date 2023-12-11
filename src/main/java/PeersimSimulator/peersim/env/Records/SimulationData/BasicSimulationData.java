package PeersimSimulator.peersim.env.Records.SimulationData;

import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.env.Tasks.ITask;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;



public class BasicSimulationData extends SimulationData {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("distance")
    private double distance;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("wi")
    private WorkerInfo wi;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("tasksCompleted")
    private List<ITask> tasksCompleted;


    //  int srcId,  List<Double> distance,  List<ITask> tasksCompleted
    @JsonCreator
    public BasicSimulationData(@JsonProperty("srcId")int srcId,
                               @JsonProperty("distance") double distance,
                               @JsonProperty("wi") WorkerInfo wi,
                               @JsonProperty("tasksCompleted") List<ITask> tasksCompleted) {
        super(srcId);
        this.distance = distance;
        this.wi = wi;
        this.tasksCompleted = tasksCompleted;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
}
