package PeersimSimulator.peersim.env.Records;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BatchAction  extends Action {

    protected int neighbourIndex;

    @JsonCreator
    public BatchAction(@JsonProperty("neighbourIndex") int neighbourIndex, @JsonProperty("controllerId") int controllerId){
        super(controllerId);
        this.neighbourIndex = neighbourIndex;
    }
    @Override
    public String toString() {
        return "target: " + neighbourIndex + " no_task: " + controllerId;
    }
    public int neighbourIndex(){
        return neighbourIndex;
    }
}