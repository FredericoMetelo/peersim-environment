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

package PeersimSimulator.example.loadbalance;

import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.config.FastConfig;

/**
 * <p>
 * This class implements the advanced load balancing scheme: each node is aware
 * of the average load of the system. If its load value is lower that the
 * average, then it pull some load with its most loaded neighbor. Otherwise, if
 * its load is higher than the average, it pushes some load to its least loaded
 * neighbor.
 * </p>
 * <p>
 * The load exchanged is limited by the {@link #PAR_QUOTA} parameter. The class
 * subclasses {@link PeersimSimulator.peersim.vector.SingleValueHolder} in order to be type
 * compatible with its observers and initializers object companions.
 * </p>
 * <p>
 * As soon as a node is "balanced" (i.e., has the same load as the average
 * value), it exits from the overlay shrinking the topology.
 * </p>
 */
public class AvgBalance extends BasicBalance {
    /**
     * The overall system average load. It is computed once by
     * {@link #calculateAVG(int)} method.
     */
    protected static double average = 0.0;

    /**
     * This flag indicates if the average value computation has been performed
     * or not. Default is NO.
     */
    protected static boolean avg_done = false;

    // ==================== initialization ================================
    // ====================================================================

    /**
     * Creates a new {@link PeersimSimulator.example.loadbalance.AvgBalance} protocol instance.
     * Reads the configuration parameters invoking the subclass constructor.
     * 
     * @param prefix
     *            The component prefix declared in the configuration file.
     */
    public AvgBalance(String prefix) {
        super(prefix); // calls the BasicBalance constructor.
    }

    // ====================== methods =====================================
    // ====================================================================

    /**
     * Calculates the system average load. Stores the result in {@link average}
     * static variable. It is run once by the first node scheduled.
     * 
     * @param protocolID
     *            the current protocol identifier.
     */
    private static void calculateAVG(int protocolID) {
        int len = Network.size();
        double sum = 0.0;
        for (int i = 0; i < len; i++) {
            AvgBalance protocol = (AvgBalance) Network.get(i).getProtocol(
                    protocolID);
            double value = protocol.getValue();
            sum += value;

        }
        average = sum / len;
        avg_done = true;
    }

    /**
     * Let a node exit from the network as soon as it has the required load
     * (equal to the average).
     * 
     * @param node
     *            the node to switch off.
     */
    protected static void suspend(Node node) {
        node.setFailState(Fallible.DOWN);
    }

    // --------------------------------------------------------------------

    // Inherited comments.
    public void nextCycle(Node node, int protocolID) {
        // Do that only once:
        if (avg_done == false) {
            calculateAVG(protocolID);
            System.out.println("AVG only once " + average);
        }

        if (Math.abs(value - average) < 1) {
            AvgBalance.suspend(node); // switch off node
            return;
        }

        if (quota == 0)
            return; // skip this node if it has no quota

        Node n = null;
        if (value < average) {
            n = getOverloadedPeer(node, protocolID);
            if (n != null) {
                doTransfer((AvgBalance) n.getProtocol(protocolID));
            }
        } else {
            n = getUnderloadedPeer(node, protocolID);
            if (n != null) {
                doTransfer((AvgBalance) n.getProtocol(protocolID));
            }
        }

        if (Math.abs(value - average) < 1)
            AvgBalance.suspend(node);
        if (n != null) {
            if (Math.abs(((AvgBalance) n.getProtocol(protocolID)).value
                    - average) < 1)
                AvgBalance.suspend(n);
        }
    }

    /**
     * Provides the most loaded neighbor according to the current node load. The
     * neighbors are extracted by the underlying {@link PeersimSimulator.peersim.core.Linkable}
     * implementing protocol.
     * 
     * @param node
     *            the current invoking (running) node.
     * @param protocolID
     *            the current protocol identifier.
     * @return the most loaded neighbor.
     */
    private Node getOverloadedPeer(Node node, int protocolID) {
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        Node neighborNode = null;
        double maxdiff = 0.0;
        for (int i = 0; i < linkable.degree(); ++i) {
            Node peer = linkable.getNeighbor(i);
            if (!peer.isUp()) // only if the neighbor is active
                continue;
            AvgBalance n = (AvgBalance) peer.getProtocol(protocolID);
            if (n.quota == 0)
                continue;
            if (value >= average && n.value >= average)
                continue;
            if (value <= average && n.value <= average)
                continue;
            double d = Math.abs(value - n.value);
            if (d > maxdiff) {
                neighborNode = peer;
                maxdiff = d;
            }
        }
        return neighborNode;
    }

    /**
     * Provides the least loaded neighbor according to the current node load.
     * The neighbors are extracted by the underlying
     * {@link PeersimSimulator.peersim.core.Linkable} implementing protocol.
     * 
     * @param node
     *            the current invoking (running) node.
     * @param protocolID
     *            the current protocol identifier.
     * @return the least loaded neighbor.
     */
    private Node getUnderloadedPeer(Node node, int protocolID) {
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        Node neighborNode = null;
        double maxdiff = 0.0;
        for (int i = 0; i < linkable.degree(); ++i) {
            Node peer = linkable.getNeighbor(i);
            if (!peer.isUp()) // only if the neighbor is active
                continue;
            AvgBalance n = (AvgBalance) peer.getProtocol(protocolID);
            if (n.quota == 0)
                continue;
            if (value >= average && n.value >= average)
                continue;
            if (value <= average && n.value <= average)
                continue;
            double d = Math.abs(value - n.value);
            if (d < maxdiff) {
                neighborNode = peer;
                maxdiff = d;
            }
        }
        return neighborNode;
    }

}
