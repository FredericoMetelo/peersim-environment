package PeersimSimulator.peersim.SDN.Links;

import PeersimSimulator.peersim.reports.GraphObserver;

public class SDNObserver extends GraphObserver {
    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------
    private static final String PAR_FILENAME_BASE = "file_base";

    private static final String PAR_COORDINATES_PROT = "coord_protocol";

    /**
     * Standard constructor that reads the configuration parameters.
     * Invoked by the simulation engine.
     *
     * @param name the configuration prefix for this class
     */
    protected SDNObserver(String name) {
        super(name);
    }

    @Override
    public boolean execute() {
        // TODO see how to communicate with Python.
        return false;
    }
}
