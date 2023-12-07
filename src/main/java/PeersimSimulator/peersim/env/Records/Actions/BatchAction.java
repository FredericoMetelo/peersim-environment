package PeersimSimulator.peersim.env.Records.Actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class BatchAction  extends Action {

    protected List<Integer> neighbourIndex;

    @JsonCreator
    public BatchAction(@JsonProperty("neighbourIndex") List<Integer> neighbourIndex, @JsonProperty("controllerId") int controllerId){
        super(controllerId);
        this.neighbourIndex = neighbourIndex;
    }
    @Override
    public String toString() {
        return "target: " + neighbourIndex + " nodeId: " + controllerId;
    }
    public List<Integer> neighbourIndexes(){
        return neighbourIndex;
    }
}