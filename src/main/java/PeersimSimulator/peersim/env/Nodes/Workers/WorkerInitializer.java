package PeersimSimulator.peersim.env.Nodes.Workers;

import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.*;

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

    private final int noLayers;
    private static final String PAR_NO_LAYERS = "NO_LAYERS";

    private final int[] numberOfNodesPerLayer;
    public static final String PAR_NO_NODES_PER_LAYERS = "NO_NODES_PER_LAYERS";

    private final double[] cpuFreqsPerLayer;
    public static final String PAR_CPU_FREQ = "FREQS";

    private final int[] coresPerLayer;
    public static final String PAR_CPU_NO_CORES = "NO_CORES";

    public final int[] qmaxPerLayer;
    private static final String PAR_Q_MAX = "Q_MAX";

    public final double[] variations;
    private static final String PAR_VARIATIONS = "VARIATIONS";


    public int hasCloud;
    private static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";


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
        noLayers = Configuration.getInt(PAR_NO_LAYERS, 1);
        hasCloud = Configuration.getInt(PAR_HAS_CLOUD, 0);
        String[] _NO_NODES_PER_LAYERS = Configuration.getString(PAR_NO_NODES_PER_LAYERS, Integer.toString(size)).split(",");

        String[] _CPU_FREQ = Configuration.getString(PAR_CPU_FREQ, "1e7").split(",");
        String[] _CPU_NO_CORES = Configuration.getString(PAR_CPU_NO_CORES, "4").split(",");
        String[] _Q_MAX = Configuration.getString(PAR_Q_MAX, "10").split(",");
        String[] _VARIATIONS = Configuration.getString(PAR_VARIATIONS, "0").split(",");

        numberOfNodesPerLayer = Arrays.stream(_NO_NODES_PER_LAYERS).mapToInt(Integer::parseInt).toArray();
        coresPerLayer = Arrays.stream(_CPU_NO_CORES).mapToInt(Integer::parseInt).toArray();
        cpuFreqsPerLayer  = Arrays.stream(_CPU_FREQ).mapToDouble(Double::parseDouble).toArray();
        qmaxPerLayer = Arrays.stream(_Q_MAX).mapToInt(Integer::parseInt).toArray();
        variations = Arrays.stream(_VARIATIONS).mapToDouble(Double::parseDouble).toArray();

        if((Arrays.stream(numberOfNodesPerLayer).sum() + hasCloud) != (size + hasCloud)
            || (noLayers != numberOfNodesPerLayer.length && Arrays.stream(numberOfNodesPerLayer).noneMatch(i -> i == 0) )
            || (noLayers != cpuFreqsPerLayer.length && Arrays.stream(cpuFreqsPerLayer).noneMatch(i -> i == 0) )
            || (noLayers != coresPerLayer.length && Arrays.stream(coresPerLayer).noneMatch(i -> i == 0) )
            || (noLayers != qmaxPerLayer.length && Arrays.stream(qmaxPerLayer).noneMatch(i -> i == 0) )
            || (noLayers != variations.length)
        ) {
            throw new RuntimeException("Mismatched number of nodes in the network and number of parameters off each layer.");
        }
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
        for(int i = 0; i < noLayers &&  offset < Network.size() - hasCloud; i++){
            int noNodes = numberOfNodesPerLayer[i];
            int noCores = coresPerLayer[i];
            double cpuFreq = cpuFreqsPerLayer[i] - sampleVariation(variations[i]);
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
        printNetStats();
        return false;
    }

    private double sampleVariation(double maxVariation){
        if(maxVariation == 0) return 0;
        return CommonState.r.nextDouble(-maxVariation, maxVariation);
    }

    private void printNetStats(){

        Log.dbg("\nWorker Statistics:\nLayers:"+formatIntArray(numberOfNodesPerLayer)+"\n");
        if(this.hasCloud == 1)
            Log.dbg("Cloud Address: "+(Network.size() - hasCloud)+"\n");
        String header = String.format("| %-4s | %-5s | %-5s | %-10s | %-10s | %-25s |%n",
                "NODE", "LAYER", "FREQS", "NO CORES", "Q SIZES", "NEIGHBOURS");
        StringBuilder rows = new StringBuilder(header);
        for (int i = 0; i < Network.size() - hasCloud; i++) {
            Node n = Network.get(i);
            Worker w = (Worker) n.getProtocol(Worker.getPid());
            int linkableID = FastConfig.getLinkable(Worker.getPid());
            Linkable l = (Linkable) n.getProtocol(linkableID);

            String row = String.format("| %-4s | %-5s | %.3e | %-3s | %-4s | %-25s |%n",
                    n.getID(), w.getLayer(), w.getCpuFreq(), w.getCpuNoCores(), w.getQueueCapacity(), l.toString());
            rows.append(row);
        }
        Log.dbg(rows.toString());
    }

    /**
     * ChatGPT written method
     * @param intArray
     * @return
     */
    private static String formatIntArray(int[] intArray) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < intArray.length; i++) {
            result.append(intArray[i]);
            if (i < intArray.length - 1) {
                result.append("  "); // Add space unless it's the last element
            }
        }
        result.append("]");
        return result.toString();
    }
}

