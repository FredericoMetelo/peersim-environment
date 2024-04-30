package PeersimSimulator.peersim.env.Nodes.Events;

public class FLUpdateEvent implements Message {
    int id;
    int src;
    int dst;
    double size;
    String key;

    public FLUpdateEvent(int id, int src, int dst, double size, String key) {
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

    public int getDst() {
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
