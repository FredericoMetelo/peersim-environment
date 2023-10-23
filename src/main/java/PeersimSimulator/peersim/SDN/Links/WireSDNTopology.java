package PeersimSimulator.peersim.SDN.Links;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.dynamics.WireGraph;
import PeersimSimulator.peersim.graph.Graph;

public class WireSDNTopology extends WireGraph {
    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------
    private static final String PAR_COORDINATES_PROT = "coord_protocol";
    private static final String PAR_RADIUS = "r";

    // --------------------------------------------------------------------------
    // Fields
    // --------------------------------------------------------------------------
    /** Coordinate protocol pid. */
    private final int coordPid;
    private final static int DEFAULT_R = 1000; // no radius
    private int r;
    // --------------------------------------------------------------------------
    // Initialization
    // --------------------------------------------------------------------------
    public WireSDNTopology(String prefix) {
        super(prefix);
        coordPid = Configuration.getPid(prefix + "." + PAR_COORDINATES_PROT); // Id of the protocol with the coordiantes in the coordinates list.
        r = Configuration.getInt(prefix + "." + PAR_RADIUS, DEFAULT_R); // Id of the protocol with the coordiantes in the coordinates list.
    }

    /**
     * Performs the actual wiring. this would technically not be static in an PeersimSimulator.SDN.
     *
     * P.S. The controller could use an API to define how the different nodes could link. For convenince
     * in initial implementations this will be static.
     *
     * @param g a {@link PeersimSimulator.peersim.graph.Graph} interface object to work on.
     *          g should be an undirected graph!
     */
    public void wire(Graph g) {

        for (int i = 0; i < Network.size(); ++i) g.setEdge(i,i);

        // Link all nodes.
        for (int i = 0; i < Network.size(); ++i) {
            // Node being linked.
            Node n = (Node) g.getNode(i);

            //Everybody knows itself

            // g.setEdge(i, i);
            for (int j = 0; j < Network.size(); j++) {
                // if(j == i) continue; Allowing Self routing, simplifies the action space.
                Node other = (Node) g.getNode(j);
                double value =  distance(n, other, coordPid);

                if(value < r && j != i){
                    g.setEdge(i, j);
                    g.setEdge(j, i);
                }
            }
        }
    }

    private static double distance(Node new_node, Node old_node, int coordPid) {
        double x1 = ((SDNNodeProperties) new_node.getProtocol(coordPid))
                .getX();
        double x2 = ((SDNNodeProperties) old_node.getProtocol(coordPid))
                .getX();
        double y1 = ((SDNNodeProperties) new_node.getProtocol(coordPid))
                .getY();
        double y2 = ((SDNNodeProperties) old_node.getProtocol(coordPid))
                .getY();
        if (x1 == -1 || x2 == -1 || y1 == -1 || y2 == -1)
            throw new RuntimeException(
                    "Found un-initialized coordinate. Use e.g., Forgot the SDNInitializer class in the config file(?).");
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }
}
