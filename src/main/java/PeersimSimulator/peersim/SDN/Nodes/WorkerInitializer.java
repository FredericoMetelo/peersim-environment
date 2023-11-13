package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;

import java.util.Arrays;

public class WorkerInitializer implements Control {
    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------


    /**
     * The protocol to operate on.
     *
     * @config
     */
    private static final String PAR_PROT = "protocol";

    private static final String PAR_NETWORK_SIZE = "SIZE";

    public final int noLayers;
    private static final String PAR_NO_LAYERS = "NO_LAYERS";

    public final int[] numberOfNodesPerLayer;
    private static final String PAR_NO_NODES_PER_LAYERS = "NO_NODES_PER_LAYERS";

    public final double[] cpuFreqsPerLayer;
    private static final String PAR_CPU_FREQ = "FREQ";

    public final int[] coresPerLayer;
    private static final String PAR_CPU_NO_CORES = "NO_CORES";

    public final int[] qmaxPerLayer;
    private static final String PAR_Q_MAX = "Q_MAX";

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /** Protocol identifier; obtained from config property {@link #PAR_PROT}. */
    private final int pid;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Creates a new instance and read parameters from the config file.
     */
    public WorkerInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);


        int size = Configuration.getInt(PAR_NETWORK_SIZE);
        noLayers = Configuration.getInt(prefix + "." + PAR_NO_LAYERS, 1);
        String[] _NO_NODES_PER_LAYERS = Configuration.getString(prefix + "." + PAR_NO_NODES_PER_LAYERS, Integer.toString(size)).split(",");

        String[] _CPU_FREQ = Configuration.getString(prefix + "." + PAR_CPU_FREQ, "1e7").split(",");
        String[] _CPU_NO_CORES = Configuration.getString(prefix + "." + PAR_CPU_NO_CORES, "4").split(",");
        String[] _Q_MAX = Configuration.getString(prefix + "." + PAR_Q_MAX, "10").split(",");

        numberOfNodesPerLayer = Arrays.stream(_NO_NODES_PER_LAYERS).mapToInt(Integer::parseInt).toArray();
        coresPerLayer = Arrays.stream(_CPU_NO_CORES).mapToInt(Integer::parseInt).toArray();
        cpuFreqsPerLayer  = Arrays.stream(_CPU_FREQ).mapToDouble(Integer::parseInt).toArray();
        qmaxPerLayer = Arrays.stream(_Q_MAX).mapToInt(Integer::parseInt).toArray();
        if(Arrays.stream(numberOfNodesPerLayer).sum() != size) throw new RuntimeException("Mismatched number of nodes in the network and nodes assigned to each layer.");
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
        // Node 0 is not a worker.
        Worker wc = ((Worker) Network.get(0).getProtocol(pid));
        wc.setActive(true); // This might be redundant...
        wc.setHasController(true);

        // Confirm feasibility. I believe that everything in the network is cloned before initialized. Not sure, go
        // through code to confirm!
        wc.setCorrespondingController((Controller) Network.get(0).getProtocol(Controller.getPid()));
        // If we want to set the controller as a worker remove the line above (start i=0 in loop)
        // Note: All Nodes have protocol Worker as True.
        int offset = 0;
        for(int i = 0; i < numberOfNodesPerLayer.length &&  offset < Network.size(); i++){
            int noNodes = numberOfNodesPerLayer[i];
            int noCores = coresPerLayer[i];
            double cpuFreq = cpuFreqsPerLayer[i];
            int qMax = qmaxPerLayer[i];
            for (int j = 0; j < noNodes; j++) {
                int id = offset + j;
                Worker w = ((Worker) Network.get(id).getProtocol(pid));
                w.setId(id);
                w.workerInit(cpuFreq, noCores, qMax, i);
            }
            offset += noNodes;
            // Set other Variables like CPU speed and others here.
        }
        return false;
    }
}
