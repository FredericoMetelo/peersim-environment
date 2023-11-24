package PeersimSimulator.peersim.env.Links;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;

import java.util.Arrays;

public class SDNInitializer implements Control {
    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------
    private static final String PAR_PROT = "protocol";
    private static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";
    private final int hasCloud;

    private static final String PAR_NETWORK_SIZE = "SIZE";

    private final int noLayers;
    private static final String PAR_NO_LAYERS = "NO_LAYERS";

    private final int[] numberOfNodesPerLayer;
    public static final String PAR_NO_NODES_PER_LAYERS = "NO_NODES_PER_LAYERS";

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------
    /** Protocol identifier, obtained from config property {@link #PAR_PROT}. */
    private static int pid;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------
    public SDNInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
        hasCloud = Configuration.getInt(PAR_HAS_CLOUD, 0);
        noLayers = Configuration.getInt(PAR_NO_LAYERS, 1);

        int size = Configuration.getInt(PAR_NETWORK_SIZE);

        String[] _NO_NODES_PER_LAYERS = Configuration.getString(PAR_NO_NODES_PER_LAYERS, Integer.toString(size)).split(",");
        numberOfNodesPerLayer = Arrays.stream(_NO_NODES_PER_LAYERS).mapToInt(Integer::parseInt).toArray();

        if((Arrays.stream(numberOfNodesPerLayer).sum()) != size){
            throw new RuntimeException("Configurations are incorrect. There are not enough nodes for all the layers and the Cloud.");
        }

    }

    // ------------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------------
    /**
     * Initialize the node coordinates. The first node in the {@link Network} is
     * the root node by default and it is located in the middle (the center of
     * the square) of the surface area.
     */
    @Override
    public boolean execute() {
        // The 0 node is the controller. We position the controller in the middle of all the nodes.
        Node n;
        SDNNodeProperties prot;
        Worker w;
        Controller c;
        // Set coordinates x,y
        for (int i = 0; i < Network.size() - hasCloud; i++) {
            n = Network.get(i);
            w = (Worker) n.getProtocol(Worker.getPid());
            c = (Controller) n.getProtocol(Controller.getPid());
            prot = (SDNNodeProperties) n.getProtocol(pid);
            // Nodes Coordinates are in 100x100 m²
            prot.setX(CommonState.r.nextDouble()*100);
            prot.setY(CommonState.r.nextDouble()*100);
            w.setProps(prot);
            c.setProps(prot);
        }
        if(hasCloud == 1){
            Node cloud = Network.get(Network.size() - 1);
            SDNNodeProperties cloudProt = (SDNNodeProperties) cloud
                    .getProtocol(pid);
            cloudProt.setX(0);
            cloudProt.setY(0);
        }
        return false;
    }
}
