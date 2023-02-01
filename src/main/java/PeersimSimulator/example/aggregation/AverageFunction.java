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

package PeersimSimulator.example.aggregation;

import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.vector.SingleValueHolder;
import PeersimSimulator.peersim.cdsim.CDProtocol;

/**
 * This class provides an implementation for the averaging function in the
 * aggregation framework. When a pair of nodes interact, their values are
 * averaged. The class subclasses {@link SingleValueHolder} in
 * order to provide a consistent access to the averaging variable value.
 *
 * Note that this class does not override the clone method, because it does
 * not have any state other than what is inherited from
 * {@link SingleValueHolder}.
 * 
 * @author Alberto Montresor
 * @version $Revision: 1.11 $
 */
public class AverageFunction extends SingleValueHolder implements CDProtocol {
    /**
     * Creates a new {@link PeersimSimulator.example.aggregation.AverageFunction} protocol
     * instance.
     * 
     * @param prefix
     *            the component prefix declared in the configuration file.
     */
    public AverageFunction(String prefix) {
        super(prefix);
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
        if (linkable.degree() > 0) {
            Node peer = linkable.getNeighbor(CommonState.r.nextInt(linkable
                    .degree()));

            // Failure handling
            if (!peer.isUp())
                return;

            AverageFunction neighbor = (AverageFunction) peer
                    .getProtocol(protocolID);
            double mean = (this.value + neighbor.value) / 2;
            this.value = mean;
            neighbor.value = mean;
        }
    }

}
