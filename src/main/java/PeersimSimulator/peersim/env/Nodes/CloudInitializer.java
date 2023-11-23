package PeersimSimulator.peersim.env.Nodes;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;

import java.util.Arrays;

public class CloudInitializer  implements Control {
    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------

    /**
     * The load at the peak node.
     *
     * @config
     */
    private static final String PAR_ACCESS_CLOUD = "CLOUD_ACCESS";
    private final int[] cloudAccessPerLayer;

    private static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";
    private final int hasCloud;
    /**
     * The protocol to operate on.
     *
     * @config
     */
    private static final String PAR_PROT = "protocol";

    public CloudInitializer(String prefix){
        hasCloud = Configuration.getInt(PAR_HAS_CLOUD, 0);
        String[] _ACCESS_CLOUD = Configuration.getString(PAR_ACCESS_CLOUD, "").split(",");
        cloudAccessPerLayer = Arrays.stream(_ACCESS_CLOUD).mapToInt(Integer::parseInt).toArray();
    }

    @Override
    public boolean execute() {
        Cloud cld = (Cloud) Network.get(Network.size() - 1).getProtocol(Cloud.getPid());
        cld.setActive(true);
        cld.init(Network.size() - 1);
        return false;
    }
}
