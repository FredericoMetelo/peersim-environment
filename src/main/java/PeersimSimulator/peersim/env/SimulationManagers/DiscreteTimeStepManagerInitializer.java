package PeersimSimulator.peersim.env.SimulationManagers;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;

public class DiscreteTimeStepManagerInitializer implements Control {

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
    public DiscreteTimeStepManagerInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
    }
    @Override
    public boolean execute() {
        AbstractTimeStepManager c = ((AbstractTimeStepManager) Network.get(0).getProtocol(pid));
        c.setActive(true);
        return false;
    }
}
