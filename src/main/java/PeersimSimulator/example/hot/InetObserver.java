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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.graph.Graph;
import PeersimSimulator.peersim.reports.GraphObserver;
import PeersimSimulator.peersim.util.FileNameGenerator;

/**
 * This class prints to files the topology wiring using a Gnuplot friendly
 * syntax. Uses the {@link PeersimSimulator.peersim.graph.Graph} interface to visit the topology.
 * 
 * @author Gian Paolo Jesi
 */
public class InetObserver extends GraphObserver {
    // ------------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------------

    /**
     * The filename base to print out the topology relations.
     * 
     * @config
     */
    private static final String PAR_FILENAME_BASE = "file_base";

    /**
     * The coordinate protocol to look at.
     * 
     * @config
     */
    private static final String PAR_COORDINATES_PROT = "coord_protocol";

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /**
     * Topology filename. Obtained from config property
     * {@link #PAR_FILENAME_BASE}.
     */
    private final String graph_filename;

    /**
     * Utility class to generate incremental indexed filenames from a common
     * base given by {@link #graph_filename}.
     */
    private final FileNameGenerator fng;

    /**
     * Coordinate protocol identifier. Obtained from config property
     * {@link #PAR_COORDINATES_PROT}.
     */
    private final int coordPid;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------
    /**
     * Standard constructor that reads the configuration parameters. Invoked by
     * the simulation engine.
     * 
     * @param prefix
     *            the configuration prefix for this class.
     */
    public InetObserver(String prefix) {
        super(prefix);
        coordPid = Configuration.getPid(prefix + "." + PAR_COORDINATES_PROT);
        graph_filename = Configuration.getString(prefix + "."
                + PAR_FILENAME_BASE, "graph_dump");
        fng = new FileNameGenerator(graph_filename, ".dat");
    }

    // Control interface method.
    public boolean execute() {
        try {
            updateGraph();

            System.out.print(name + ": ");

            // initialize output streams
            String fname = fng.nextCounterName();
            FileOutputStream fos = new FileOutputStream(fname);
            System.out.println("Writing to file " + fname);
            PrintStream pstr = new PrintStream(fos);

            // dump topology:
            graphToFile(g, pstr, coordPid);

            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    /**
     * Utility method: prints out data to plot the topology using gnuplot a
     * gnuplot style.
     * 
     * @param g
     *            current graph.
     * @param ps
     *            a {@link java.io.PrintStream} object to write to.
     * @param coordPid
     *            coordinate protocol identifier.
     */
    private static void graphToFile(Graph g, PrintStream ps, int coordPid) {
        for (int i = 1; i < g.size(); i++) {
            Node current = (Node) g.getNode(i);
            double x_to = ((InetCoordinates) current
                    .getProtocol(coordPid)).getX();
            double y_to = ((InetCoordinates) current
                    .getProtocol(coordPid)).getY();
            for (int index : g.getNeighbours(i)) {
                Node n = (Node) g.getNode(index);
                double x_from = ((InetCoordinates) n
                        .getProtocol(coordPid)).getX();
                double y_from = ((InetCoordinates) n
                        .getProtocol(coordPid)).getY();
                ps.println(x_from + " " + y_from);
                ps.println(x_to + " " + y_to);
                ps.println();
            }
        }
    }
}
