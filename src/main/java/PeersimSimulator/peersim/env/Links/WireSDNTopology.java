package PeersimSimulator.peersim.env.Links;

import PeersimSimulator.peersim.env.Nodes.WorkerInitializer;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.dynamics.WireGraph;
import PeersimSimulator.peersim.graph.Graph;

import java.util.Arrays;

public class WireSDNTopology extends WireGraph {
    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------
    private static final String PAR_COORDINATES_PROT = "coord_protocol";
    private static final String PAR_RADIUS = "r";

    private static final String PAR_ACCESS_CLOUD = "CLOUD_ACCESS";
    private static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";

    // --------------------------------------------------------------------------
    // Fields
    // --------------------------------------------------------------------------
    /** Coordinate protocol pid. */
    private final int coordPid;
    private final static int DEFAULT_R = 1000; // no radius
    private final int[] cloudAccessPerLayer;
    private final int hasCloud;

    private int[] layers;
    private int r;
    // --------------------------------------------------------------------------
    // Initialization
    // --------------------------------------------------------------------------
    public WireSDNTopology(String prefix) {
        super(prefix);
        coordPid = Configuration.getPid(prefix + "." + PAR_COORDINATES_PROT); // Id of the protocol with the coordiantes in the coordinates list.
        r = Configuration.getInt(prefix + "." + PAR_RADIUS, DEFAULT_R); // Id of the protocol with the coordiantes in the coordinates list.

        String[] _layers = Configuration.getString(WorkerInitializer.PAR_NO_NODES_PER_LAYERS).split(",");
        layers = Arrays.stream(_layers).mapToInt(Integer::parseInt).toArray();


        hasCloud = Configuration.getInt(PAR_HAS_CLOUD, 0);
        String[] _ACCESS_CLOUD = Configuration.getString(PAR_ACCESS_CLOUD, "0").split(",");
        cloudAccessPerLayer = Arrays.stream(_ACCESS_CLOUD).mapToInt(Integer::parseInt).toArray();
    }

    /**
     * Performs the actual wiring. this would technically not be static in an PeersimSimulator.env.
     *
     * P.S. The controller could use an API to define how the different nodes could link. For convenince
     * in initial implementations this will be static.
     *
     * @param g a {@link PeersimSimulator.peersim.graph.Graph} interface object to work on.
     *          g should be an undirected graph!
     */
    public void wire(Graph g) {


        // Link all nodes.
        for (int i = 0; i < Network.size(); ++i) {
            g.setEdge(i, i);
        }

        int offset = 0;
        int s = 0;
        int e = layers[0];
        for(int l = 0; l < layers.length; l++){
            s += (l > 1) ? layers[l - 2] : 0;
            e +=  (layers.length > l + 1)? layers[l + 1] : 0;
            for(int i = offset; i < offset + layers[l]; i++){
                Node n = (Node) g.getNode(i);
                // Node being linked.
                if(this.layerCloudAccess(l)) {
                    g.setEdge(i, Network.size() - 1);
                }

                for (int j = s; j < e; j++) {
                    // if(j == i) continue; Allowing Self routing, simplifies the action space.
                    Node other = (Node) g.getNode(j);
                    double value =  distance(n, other, coordPid);
                    if(value < r && j != i){
                        g.setEdge(i, j);
                        g.setEdge(j, i);
                    }
                }
            }
            offset += layers[l];
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

    private boolean layerCloudAccess(int layer){
        return hasCloud == 1 && cloudAccessPerLayer[layer] == 1;
    }
}
