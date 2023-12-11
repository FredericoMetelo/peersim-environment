package PeersimSimulator.peersim.env.Records.SimulationData;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicSimulationData.class, name = "basicSD"),
        @JsonSubTypes.Type(value = BatchSimulationData.class, name = "batchSD"),
        @JsonSubTypes.Type(value = FailledActionSimulationData.class, name = "failledActionSD"),
})
public abstract class SimulationData {
        protected int srcId;


        public SimulationData(int srcId){
                this.srcId = srcId;
        }

        public int getSrcId() {
                return srcId;
        }

        public void setSrcId(int srcId) {
                this.srcId = srcId;
        }
}


