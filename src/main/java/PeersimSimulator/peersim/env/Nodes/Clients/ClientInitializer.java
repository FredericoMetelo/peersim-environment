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

    private static final String LAYERS_THAT_GENERATE_TASKS = "clientLayers";

    private static final String CLIENT_IS_SELF = "clientIsSelf";

    private static final String LAYERS_THAT_GET_TASKS = "layersThatGetTasks";

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /** Protocol identifier; obtained from config property {@link #PAR_PROT}. */
    private final int pid;

    private final int clientIsSelf;

    private final int[] layersThatGetTasks;

    private final int[] layers;
    private final int[] clientLayers;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Creates a new instance and read parameters from the config file.
     */
    public ClientInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);

        clientIsSelf = Configuration.getInt(CLIENT_IS_SELF);

        String[] _layersThatGetTasks = Configuration.getString(LAYERS_THAT_GET_TASKS).split(",");
        layersThatGetTasks = Arrays.stream(_layersThatGetTasks).mapToInt(Integer::parseInt).toArray();

        String[] _layers = Configuration.getString(WorkerInitializer.PAR_NO_NODES_PER_LAYERS).split(",");
        layers = Arrays.stream(_layers).mapToInt(Integer::parseInt).toArray();

        String[] _clientLayers = Configuration.getString(LAYERS_THAT_GENERATE_TASKS).split(",");
        clientLayers = Arrays.stream(_clientLayers).mapToInt(Integer::parseInt).toArray();
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
        int base = 0;
        for( int layer = 0 ; layer < layers.length; layer++) {
            int l = layer;
            if (Arrays.stream(clientLayers).anyMatch(x -> x == l)) {
                // TODO need to guarantee that if the task production is for the node only, and the worker in this node
                //  does not get tasks, then the client should not be active.
                for (int i = base; i < base + layers[layer]; i++) {
                    Client c = ((Client) Network.get(i).getProtocol(pid));
                    if(clientIsSelf == 1 && Arrays.stream(layersThatGetTasks).noneMatch(x -> x == l)){
                        c.setActive(false);
                    }else {
                        c.setActive(true);
                    }
                    c.setId(i);
                    // Set other Variables like CPU speed and others here.
                }
             }
            base += layers[layer];
        }
        return false;
    }
}