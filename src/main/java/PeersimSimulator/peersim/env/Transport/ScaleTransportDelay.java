package PeersimSimulator.peersim.env.Transport;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDSimulator;
import PeersimSimulator.peersim.transport.Transport;
import org.lsmp.djep.xjep.function.Max;

public class ScaleTransportDelay implements Transport {
    private static final String PAR_TRANSPORT = "transport";

    public static final String PAR_SCALE = "SCALE";
    private int scale;
    private final int transport;

    public ScaleTransportDelay(String prefix) {
        this.transport = Configuration.getPid(prefix+"."+PAR_TRANSPORT);
        this.scale = Configuration.getInt(PAR_SCALE, 1);
    }

    /**
     * Returns <code>this</code>. This way only one instance exists in the system
     * that is linked from all the nodes. This is because this protocol has no
     * node specific state.
     */
    public Object clone()
    {
        return this;
    }

//---------------------------------------------------------------------
//Methods
//---------------------------------------------------------------------

    /**
     * Delivers the message with a random
     * delay, that is drawn from the configured interval according to the uniform
     * distribution.
     */
    public void send(Node src, Node dest, Object msg, int pid)
    {
        // avoid calling nextLong if possible
        MaxCapacityTransport t = (MaxCapacityTransport) src.getProtocol(transport);
        long delay = t.getMessageLatency(src, dest, msg, pid);
        delay = delay * scale; // removed (delay == 1) ? 1 : ...
        EDSimulator.add(delay, msg, dest, pid);
    }

    /**
     * Returns a random
     * rescales the delay to the range of the configured interval according to the uniform
     */
    public long getLatency(Node src, Node dest)
    {
        Transport t = (Transport) src.getProtocol(transport);
        return t.getLatency(src, dest) * scale;
    }

}
