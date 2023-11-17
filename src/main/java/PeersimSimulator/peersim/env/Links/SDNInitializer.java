package PeersimSimulator.peersim.env.Links;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;

public class SDNInitializer implements Control {
    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------
    private static final String PAR_PROT = "protocol";
    private static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";
    private final int hasCloud;
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
        Node n = Network.get(0);
        SDNNodeProperties prot = (SDNNodeProperties) n
                .getProtocol(pid);
        prot.setX(50);
        prot.setY(50);

        // Set coordinates x,y
        for (int i = 1; i < Network.size() - hasCloud; i++) {
            n = Network.get(i);
            prot = (SDNNodeProperties) n.getProtocol(pid);
            // Nodes Coordinates are in 100x100 m²
            prot.setX(CommonState.r.nextDouble()*100);
            prot.setY(CommonState.r.nextDouble()*100);
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
