package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.env.Nodes.Workers.WorkerInitializer;

import java.util.Arrays;

public class ClientInitializer  implements Control {
    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------

    /**
     * The load at the peak node.
     *
     * @config
     */
    private static final String PAR_VALUE = "value";

    /**
     * The protocol to operate on.
     *
     * @config
     */
    private static final String PAR_PROT = "protocol";
    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /** Protocol identifier; obtained from config property {@link #PAR_PROT}. */
    private final int pid;
    private final int[] layers;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Creates a new instance and read parameters from the config file.
     */
    public ClientInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);

        String[] _layers = Configuration.getString(WorkerInitializer.PAR_NO_NODES_PER_LAYERS).split(",");
        layers = Arrays.stream(_layers).mapToInt(Integer::parseInt).toArray();
    }

    // ------------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------------

    /**
     * Initialize an aggregation protocol using a peak distribution.
     * That is, one node will get the peek value, the others zero.
     * @return always false
     */
    public boolean execute() {
        // Initialize the Clients
        for(int i = 0; i < layers[0]; i++){
            Client c = ((Client) Network.get(i).getProtocol(pid));
            c.setActive(true);
            c.setId(i);
            // Set other Variables like CPU speed and others here.
        }
        return false;
    }
}