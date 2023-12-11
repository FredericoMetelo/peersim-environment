package PeersimSimulator.peersim.env.Records.SimulationData;

import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.env.Tasks.ITask;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FailledActionSimulationData  extends SimulationData {{
}@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonProperty("tasksCompleted")
private List<ITask> tasksCompleted;


    //  int srcId,  List<Double> distance,  List<ITask> tasksCompleted
    @JsonCreator
    public FailledActionSimulationData(@JsonProperty("srcId")int srcId) {
        super(srcId);
    }
}
