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

import PeersimSimulator.peersim.config.*;
import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.util.*;
import PeersimSimulator.peersim.vector.*;

/**
 * This class monitors the state of the load balancing process at each cycle. It
 * is assumed that the network nodes comply to the
 * {@link PeersimSimulator.peersim.vector.SingleValue} interface.
 * 
 */
public class LBObserver implements Control {

    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------

    /**
     * The protocol to operate on.
     * 
     * @config
     */
    private static final String PAR_PROT = "protocol";

    /**
     * If defined, print the load value. Not defined by default.
     * 
     * @config
     */
    private static final String PAR_SHOW_VALUES = "show_values";

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /**
     * The name of this observer in the configuration file. Initialized by the
     * constructor parameter.
     */
    private final String name;

    /** Protocol identifier, obtained from config property {@link #PAR_PROT}. */
    private final int pid;

    /**
     * Flag to show or not the load values at each node; obtained from config
     * property {@link #PAR_SHOW_VALUES}.
     */
    private boolean show_values;

    /**
     * This object keeps track of the values injected and produces statistics.
     * More details in: {@link PeersimSimulator.peersim.util.IncrementalStats} .
     */
    private IncrementalStats stats = null;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Standard constructor that reads the configuration parameters. Invoked by
     * the simulation engine.
     * 
     * @param name
     *            the configuration prefix identifier for this class.
     */
    public LBObserver(String name) {
        this.name = name;
        // Other parameters from config file:
        pid = Configuration.getPid(name + "." + PAR_PROT);
        show_values = Configuration.contains(name + "." + PAR_SHOW_VALUES);
        stats = new IncrementalStats();
    }

    // ------------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------------

    // Inherits comments from the interface.
    public boolean execute() {
        StringBuffer buf = new StringBuffer();
        int count_zero = 0;

        if (show_values) {
            buf.append(name+": ");
        }

        /* Compute max, min, average */
	final int len = Network.size();
        for (int i = 0; i < len; i++) {
            SingleValue prot = (SingleValue) Network.get(i).getProtocol(pid);
            double value = prot.getValue();
            stats.add(value);

            if (value == 0) {
                count_zero++;
            }
            // shows the values of load at each node:
            if (show_values) {
                buf.append(value+":");
            }
            
        }
        if (show_values) {
            System.out.println(buf.toString());
        }

        System.out.println(name + ": " + CommonState.getTime() + " "
                + stats.getAverage() + " " + stats.getMax() + " "
                + stats.getMin() + " " + count_zero + " " + // number of zero value node
                stats.getVar());
        stats.reset();
        return false;

    }

}
