package PeersimSimulator.peersim.env.Records;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class FLUpdate{
int src; int dst; double size; String uuid;

    @JsonCreator
    public FLUpdate(@JsonProperty("src") int src, @JsonProperty("dst") int dst, @JsonProperty("size") double size, @JsonProperty("uuid") String uuid) {
        this.src = src;
        this.dst = dst;
        this.size = size;
        this.uuid = uuid;
    }

    public int getSrc() {
        return src;
    }

    public int getDst() {
        return dst;
    }

    public double getSize() {
        return size;
    }

    public String getUuid() {
        return uuid;
    }

    public void setSrc(int src) {
        this.src = src;
    }

    public void setDst(int dst) {
        this.dst = dst;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
