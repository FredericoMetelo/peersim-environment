package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;

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
        for(int i = 0; i<Network.size(); i++){
            Worker w = ((Worker) Network.get(i).getProtocol(pid));
            w.setId(i);
            // Set other Variables like CPU speed and others here.
        }
        return false;
    }
}
