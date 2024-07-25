package PeersimSimulator.peersim.env.Nodes.Events;

import PeersimSimulator.peersim.env.Nodes.Cloud.Cloud;
import PeersimSimulator.peersim.env.Tasks.ITask;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Queue;

public class CloudInfo implements Message{

    @JsonProperty("defaultProcessingPower")
    private double defaultProcessingPower;

    @JsonProperty("queueSize")
    private int queueSize;

    @JsonProperty("freeVMs")
    private int freeVMs;

    @JsonProperty("totalVMs")
    private int totalVMs;

    /**
     * The cloud internal id on the network
     */
    @JsonProperty("id")
    private int id;

    public CloudInfo(
            @JsonProperty("defaultProcessingPower") double defaultProcessingPower,
            @JsonProperty("queueSize") int queueSize,
            @JsonProperty("freeVMs") int freeVMs,
            @JsonProperty("totalVMs") int totalVMs,
            @JsonProperty("id") int id
    ) {
        this.defaultProcessingPower = defaultProcessingPower;
        this.queueSize = queueSize;
        this.freeVMs = freeVMs;
        this.totalVMs = totalVMs;
        this.id = id;
    }

    public double getDefaultProcessingPower() {
        return defaultProcessingPower;
    }

    public void setDefaultProcessingPower(double defaultProcessingPower) {
        this.defaultProcessingPower = defaultProcessingPower;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getFreeVMs() {
        return freeVMs;
    }

    public void setFreeVMs(int freeVMs) {
        this.freeVMs = freeVMs;
    }

    public int getTotalVMs() {
        return totalVMs;
    }

    public void setTotalVMs(int totalVMs) {
        this.totalVMs = totalVMs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public double getSize() {
        return 0;
    }
}
