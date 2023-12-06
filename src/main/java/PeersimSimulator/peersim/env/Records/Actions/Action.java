package PeersimSimulator.peersim.env.Records.Actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// May the beautiful people that make tutorials at Baeldung be blessed with 100 years of health for them and their children.
// And may the beautiful people at OpenAI also be blessed with 50 years of health for them and their children for the
// creation of ChatGPT, assuming they don't screw up the world with all those for-profit shenanigans.
// src: https://www.baeldung.com/jackson-inheritance + ChatGPT

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
