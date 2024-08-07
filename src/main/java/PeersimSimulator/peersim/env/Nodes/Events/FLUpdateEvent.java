package PeersimSimulator.peersim.env.Nodes.Events;

import java.util.List;

public class FLUpdateEvent implements Message {
    int id;
    int src;
    List<Integer> path;
    double size;
    String key;
    int dst;

    public FLUpdateEvent(int id, int src, int dst, List<Integer> path, double size, String key) {
        this.id = id;
        this.src = src;
        this.dst = dst;
        this.path = path;
        this.size = size;
        this.key = key;
    }

    public int getId() {
        return id;
    }

    public int getSrc() {
        return src;
    }

    public List<Integer> getPath() {
        return path;
    }

    public String getKey() {
        return key;
    }

    public int getDst() {
        return dst;
    }

    public void setDst(int dst) {
        this.dst = dst;
    }

    @Override
    public double getSize() {
        return size;
    }
}
