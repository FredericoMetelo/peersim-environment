package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;

public class ControllerInitializer implements Control {
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

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Creates a new instance and read parameters from the config file.
     */
    public ControllerInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
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
        // Initialize the controller (Node 0).
        Controller c = ((Controller) Network.get(0).getProtocol(pid));
        c.setActive(true);
        c.setId(0);
        c.initializeWorkerInfo(Network.get(0), Controller.getPid());
        c.setCorrespondingWorker(((Worker) Network.get(0).getProtocol(Worker.getPid())));

        return false;
    }
}
