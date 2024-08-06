package PeersimSimulator.peersim.env.Nodes.Events;

import java.util.List;

public class FLUpdateEvent implements Message {
    int id;
    int src;
    List<Integer> dst;
    double size;
    String key;

    public FLUpdateEvent(int id, int src, List<Integer> dst, double size, String key) {
        this.id = id;
        this.src = src;
        this.dst = dst;
        this.size = size;
        this.key = key;
    }

    public int getId() {
        return id;
    }

    public int getSrc() {
        return src;
    }

    public List<Integer> getDst() {
        return dst;
    }

    public String getKey() {
        return key;
    }

    @Override
    public double getSize() {
        return size;
    }
}
