package PeersimSimulator.peersim.env.Records.Actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// guide to dealing with the Jackson problems. src: https://www.baeldung.com/jackson-inheritance

@JsonTypeInfo
        (use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicAction.class, name = "basic"),
        @JsonSubTypes.Type(value = BatchAction.class, name = "batch")
})
public abstract class Action {
    protected int controllerId;

    @JsonCreator
    public Action(@JsonProperty("controllerId") int controllerId){
        this.controllerId = controllerId;
    }


    public int controllerId(){
        return controllerId;
    }
}
