package PeersimSimulator.peersim.env.Nodes;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;

import java.util.Arrays;
import java.util.List;

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

    private static final String PAR_CONTROLLERS = "CONTROLLERS";

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
    private List<Integer> controllers;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Creates a new instance and read parameters from the config file.
     */
    public ControllerInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
        controllers = Arrays.stream(Configuration.getString(PAR_CONTROLLERS, "0")
                .split(";"))
                .distinct()
                .map(Integer::parseInt)
                .toList();
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
        // Initialize the controller.
        if(controllers.size() > this.controllers.size()) throw new RuntimeException("Too many controllers!");

        for (int id : controllers){
            if(id < 0 || id > Network.size()) throw new RuntimeException("Invalid id for a controller. There are " + id + "< 0  or " + id + ">" +  Network.size());
            Controller c = ((Controller) Network.get(id).getProtocol(pid));
            c.setActive(true);
            c.setId(id);
            c.initializeWorkerInfo(Network.get(id), Controller.getPid());
            c.setCorrespondingWorker(((Worker) Network.get(id).getProtocol(Worker.getPid())));
        }
        return false;
    }
}
