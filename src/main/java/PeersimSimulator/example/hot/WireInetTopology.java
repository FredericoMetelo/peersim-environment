/*
 * Copyright (c) 2003-2005 The BISON Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 */

package PeersimSimulator.example.hot;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.dynamics.WireGraph;
import PeersimSimulator.peersim.graph.Graph;

/**
 * This class applies a HOT topology on a any {@link Linkable} implementing
 * protocol.
 * 
 * @author Gian Paolo Jesi
 */
public class WireInetTopology extends WireGraph {
    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------
    /**
     * The alpha parameter. It affects the distance relevance in the wiring
     * process. Default value: 0.5.
     * 
     * @config
     */
    private static final String PAR_ALPHA = "alpha";

    /**
     * The coordinate protocol to look at.
     * 
     * @config
     */
    private static final String PAR_COORDINATES_PROT = "coord_protocol";

    // --------------------------------------------------------------------------
    // Fields
    // --------------------------------------------------------------------------
    /* A parameter that affects the distance importance. */
    private final double alpha;

    /** Coordinate protocol pid. */
    private final int coordPid;

    // --------------------------------------------------------------------------
    // Initialization
    // --------------------------------------------------------------------------

    /**
     * Standard constructor that reads the configuration parameters. Normally
     * invoked by the simulation engine.
     * 
     * @param prefix
     *            the configuration prefix for this class
     */
    public WireInetTopology(String prefix) {
        super(prefix);
        alpha = Configuration.getDouble(prefix + "." + PAR_ALPHA, 0.5);
        coordPid = Configuration.getPid(prefix + "." + PAR_COORDINATES_PROT);
    }

    /**
     * Performs the actual wiring.
     * @param g a {@link PeersimSimulator.peersim.graph.Graph} interface object to work on.
     */
    public void wire(Graph g) {
        /** Contains the distance in hops from the root node for each node. */
        int[] hops = new int[Network.size()];
        // connect all the nodes other than roots
        for (int i = 1; i < Network.size(); ++i) {
            Node n = (Node) g.getNode(i);

            // Look for a suitable parent node between those allready part of
            // the overlay topology: alias FIND THE MINIMUM!
            // Node candidate = null;
            int candidate_index = 0;
            double min = Double.POSITIVE_INFINITY;
            for (int j = 0; j < i; j++) {
                Node parent = (Node) g.getNode(j);
                double jHopDistance = hops[j];

                double value = jHopDistance
                        + (alpha * distance(n, parent, coordPid));
                if (value < min) {
                    // candidate = parent; // best parent node to connect to
                    min = value;
                    candidate_index = j;
                }
            }

            hops[i] = hops[candidate_index] + 1;
            g.setEdge(i, candidate_index);
        }
    }

    /**
     * Utility function: returns the Euclidean distance based on the x,y
     * coordinates of a node. A {@link RuntimeException} is raised if a not
     * initialized coordinate is found.
     * 
     * @param new_node
     *            the node to insert in the topology.
     * @param old_node
     *            a node already part of the topology.
     * @param coordPid
     *            identifier index.
     * @return the distance value.
     */
    private static double distance(Node new_node, Node old_node, int coordPid) {
        double x1 = ((InetCoordinates) new_node.getProtocol(coordPid))
                .getX();
        double x2 = ((InetCoordinates) old_node.getProtocol(coordPid))
                .getX();
        double y1 = ((InetCoordinates) new_node.getProtocol(coordPid))
                .getY();
        double y2 = ((InetCoordinates) old_node.getProtocol(coordPid))
                .getY();
        if (x1 == -1 || x2 == -1 || y1 == -1 || y2 == -1)
            throw new RuntimeException(
                    "Found un-initialized coordinate. Use e.g., InetInitializer class in the config file.");
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }
}
