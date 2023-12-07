package PeersimSimulator.peersim.env.Records.Actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BasicAction extends Action {

    protected int neighbourIndex;

    @JsonCreator
    public BasicAction(@JsonProperty("neighbourIndex") int neighbourIndex, @JsonProperty("controllerId") int controllerId){
        super(controllerId);
        this.neighbourIndex = neighbourIndex;
    }
    @Override
    public String toString() {
        return "targets: " + neighbourIndex + " nodeId: " + controllerId;
    }
    public int neighbourIndex(){
        return neighbourIndex;
    }
}

