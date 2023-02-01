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

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.vector.SingleValueHolder;
import PeersimSimulator.peersim.cdsim.CDProtocol;

/**
 * <p>
 * This class implements a (simple) load balancing strategy: each node selects
 * its most "distant" neighbor in terms of load difference and exchanges with it
 * an amount of load not exceeding the {@link #PAR_QUOTA} parameter.
 * </p>
 * <p>
 * The class subclasses {@link PeersimSimulator.peersim.vector.SingleValueHolder} in order to be
 * type compatible with its observers and initializers object companions.
 * </p>
 * 
 */
public class BasicBalance extends SingleValueHolder implements CDProtocol {

    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------

    /**
     * Initial quota. Defaults to 1.
     * 
     * @config
     */
    protected static final String PAR_QUOTA = "quota";

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /** Quota amount. Obtained from config property {@link #PAR_QUOTA}. */
    private final double quota_value;

    protected double quota; // current cycle quota

    // ------------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------------
    /**
     * Standard constructor that reads the configuration parameters. Invoked by
     * the simulation engine.
     * 
     * @param prefix
     *            the configuration prefix for this class.
     */
    public BasicBalance(String prefix) {
        super(prefix);
        // get quota value from the config file. Default 1.
        quota_value = (Configuration.getInt(prefix + "." + PAR_QUOTA, 1));
        quota = quota_value;
    }

    // The clone() method is inherited.

    // ------------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------------

    /** Resets the current node quota value. */
    protected void resetQuota() {
        this.quota = quota_value;
    }

    /**
     * Using an underlying {@link Linkable} protocol choses a neighbor and
     * performs a variance reduction step.
     * 
     * @param node
     *            the node on which this component is run.
     * @param protocolID
     *            the id of this protocol in the protocol array.
     */
    public void nextCycle(Node node, int protocolID) {
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        if (this.quota == 0) {
            return; // quota is exceeded
        }
        // this takes the most distant neighbor based on local load
        BasicBalance neighbor = null;
        double maxdiff = 0;
        for (int i = 0; i < linkable.degree(); ++i) {
            Node peer = linkable.getNeighbor(i);
            // The selected peer could be inactive
            if (!peer.isUp())
                continue;
            BasicBalance n = (BasicBalance) peer.getProtocol(protocolID);
            if (n.quota == 0.0)
                continue;
            double d = Math.abs(value - n.value);
            if (d > maxdiff) {
                neighbor = n;
                maxdiff = d;
            }
        }
        if (neighbor == null) {
            return;
        }
        doTransfer(neighbor);
    }

    /**
     * Performs the actual load exchange selecting to make a PUSH or PULL
     * approach. It affects the involved nodes quota.
     * 
     * @param neighbor
     *            the selected node to talk with. It is assumed that it is an
     *            instance of the {@link PeersimSimulator.example.loadbalance.BasicBalance}
     *            class.
     */
    protected void doTransfer(BasicBalance neighbor) {
        double a1 = this.value;
        double a2 = neighbor.value;
        double maxTrans = Math.abs((a1 - a2) / 2);
        double trans = Math.min(maxTrans, quota);
        trans = Math.min(trans, neighbor.quota);
        if (a1 <= a2) // PULL phase
        {
            a1 += trans;
            a2 -= trans;
        } else // PUSH phase
        {
            a1 -= trans;
            a2 += trans;
        }
        this.value = a1;
        this.quota -= trans;
        neighbor.value = a2;
        neighbor.quota -= trans;
    }

}
