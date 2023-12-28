package PeersimSimulator.peersim.env.Links;

import PeersimSimulator.peersim.env.Nodes.Workers.WorkerInitializer;
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

    private static final String TOPOLOGY = "TOPOLOGY";

    private static final String PAR_ACCESS_CLOUD = "CLOUD_ACCESS";
    private static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";

    private static final String PAR_RANDOMIZE_TOPOLOGY = "RANDOMIZETOPOLOGY";
    private static final String PAR_LINKS = "TOPOLOGY";

    // --------------------------------------------------------------------------
    // Fields
    // --------------------------------------------------------------------------
    /** Coordinate protocol pid. */
    private final int coordPid;
    private final boolean RANDOMIZE_TOPOLOGY;
    private final String[] links;
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

        RANDOMIZE_TOPOLOGY = Configuration.getBoolean(PAR_RANDOMIZE_TOPOLOGY, true);

        this.links = Configuration.getString(prefix + "." +PAR_LINKS, "").split(";");

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
        if(this.RANDOMIZE_TOPOLOGY)
            radiusBasedTopology(g);
        else
            specifiedTopology(g);
    }

    private void specifiedTopology(Graph g) {
        if(this.links.length == 0)
            throw new RuntimeException("No links specified.");
        if(this.links.length != (Network.size() - hasCloud))
            throw new RuntimeException("Every node must have at least a link to itself on the first entry.");

        for (int i = 0; i < Network.size(); ++i) {
            g.setEdge(i, i);
        }

        for(int i = 0; i < this.links.length; i++){
            String links = this.links[i];
            String[] _links = links.split(",");
            int from = Integer.parseInt(_links[0]); // IMPORTANT!!! FIRST Entry must always be the node itself.
            if(i != from){
                throw new RuntimeException("Link specification must be ordered by node, meaning node 0 needs to be specified first, then node 1, and so forth. \n" +
                        "The first entry must always be the node itself.");
            }
            for(int j = 1; j < _links.length; j++){
                int to = Integer.parseInt(_links[j]);
                g.setEdge(from, to);
                g.setEdge(to, from);
            }
        }
        int j = 0;
        int cloud = Network.size() - 1;
        for(int l = 0; l < layers.length; l++){
            int nodesInLayer = j + layers[l];
            while(j < nodesInLayer){
                if(this.layerCloudAccess(l)) {
                    g.setEdge(j, cloud);
                    g.setEdge(cloud, j);
                }
                j++;
            }

        }
    }

    /**
     *
     * From a random positioning of the nodes, generates a topology based on the radius.
     * This one was 100% me and I'm kinda proud of it.
     * @param g
     */
    private void radiusBasedTopology(Graph g) {
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

                for (int j = s; j < e; j++) {
                    // if(j == i) continue; Allowing Self routing, simplifies the action space.
                    Node other = (Node) g.getNode(j);
                    double value =  distance(n, other, coordPid);
                    if(value < r && j != i){
                        g.setEdge(i, j);
                        g.setEdge(j, i);
                    }
                }

                if(this.layerCloudAccess(l)) {
                    g.setEdge(i, Network.size() - 1);
                    g.setEdge(Network.size() - 1, i);
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
