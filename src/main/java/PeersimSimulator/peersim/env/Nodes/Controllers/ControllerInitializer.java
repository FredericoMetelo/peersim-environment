package PeersimSimulator.peersim.env.Nodes.Controllers;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.transport.Transport;

import java.util.*;

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
        // Note that this object is shared among all the controllers. No loss of generality as we are dealing with a static network.
        Map<String, List<Integer>> paths = new HashMap<>();
        for (int id : controllers){
            if(id < 0 || id > Network.size()) throw new RuntimeException("Invalid id for a controller. There are " + id + "< 0  or " + id + ">" +  Network.size());
            this.generatePaths(id, controllers, paths);
            Controller c = ((Controller) Network.get(id).getProtocol(pid));
            c.setActive(true);
            c.setId(id);
            c.initializeWorkerInfo(Network.get(id), Controller.getPid());
            c.setCorrespondingWorker(((Worker) Network.get(id).getProtocol(Worker.getPid())));
            c.setPathsToOtherControllers(paths);
        }
        return false;
    }

    private void generatePaths(int id, List<Integer> controllers, Map<String, List<Integer>> paths){
        for(int controller : controllers){
            if(controller == id) continue;
            List<Integer> path = a_star(id, controller);
            paths.put(id + "-" +  controller, path);
        }
    }

    /**
     * A* algorithm to find the shortest path between two nodes. Meant to be used to find the optimal path between all the controllers.
     * Btw, this method requires some optimization...
     * @param start_node
     * @param end_node
     * @return list of nodes between the start and end node.
     */
    public List<Integer> a_star(int start_node, int end_node){
        // Get the opening set
        PriorityQueue<AStarEntry> openSet = new PriorityQueue<>((a, b) -> (int) Math.round(a.getF() - b.getF())); // Boi I love Kotlin and making java improve on the side <3. Btw, this is not exactly safe... -\_(o.o)_/-
        SDNNodeProperties end = (SDNNodeProperties) Network.get(end_node).getProtocol(SDNNodeProperties.getPid());

        SDNNodeProperties start = (SDNNodeProperties) Network.get(start_node).getProtocol(SDNNodeProperties.getPid());
        // Add first node to the open set.
        openSet.add(new AStarEntry(start_node, 0, 0, start.distanceTo(end.getCoordinates()), null));

        while(!openSet.isEmpty()){
            AStarEntry current = openSet.poll();
            if(current.node == end_node){
                // We have reached the end node.
                // Now we need to reconstruct the path.
                return rebuildPath(current);
            }
            // We need to get the neighbors of the current node.
            Linkable linkable = (Linkable) Network.get(current.node).getProtocol(FastConfig.getLinkable(pid));
            for(int i = 0; i < linkable.degree(); i++){
                if(!linkable.getNeighbor(i).isUp() || linkable.getNeighbor(i).getID() == current.node) continue; // TODO when adding node movement, this will need to be updated!!!

                Node currentNode = Network.get(current.node);
                Node neighbourNode =linkable.getNeighbor(i);
                int neighbor = neighbourNode.getIndex();
                SDNNodeProperties neighbourSDNProps = (SDNNodeProperties) Network.get(neighbor).getProtocol(SDNNodeProperties.getPid());
                double latency = ((Transport) Network.get(current.node).getProtocol(FastConfig.getTransport(pid))).getLatency(currentNode, neighbourNode);
                // g is the  latency of sending a 100Mb message to the neighbor.
                double tentative_g = current.g + latency;
                // h is the Eucidean distance to the end node.
                double tentative_h = neighbourSDNProps.distanceTo(end.getCoordinates());
                // f is the total cost of the path.
                double tentative_f = tentative_g + tentative_h;

                // Check if the neighbor is in the open set.
                boolean inOpenSet = false;
                for(AStarEntry entry : openSet){
                    if(entry.node == neighbor){
                        inOpenSet = true;
                        if(tentative_g < entry.g){ // Distance doesn't matter, swap if latency to that point was smaller, otherwise ignore.
                            // Note to self, could speed this up with a map.
                            entry.setG(tentative_g);
                            entry.setF(tentative_f);
                            entry.setParent(current);
                        }
                        break;
                    }
                }
                if(!inOpenSet){
                    openSet.add(new AStarEntry(neighbor, tentative_f, tentative_g, tentative_h, current));
                }
            }
        }
        return null;
    }

    public List<Integer> rebuildPath(AStarEntry end){
        List<Integer> path = new ArrayList<>();
        AStarEntry current = end;
        while(current != null){
            path.add(current.node);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private class AStarEntry{
        /**
         * The node that the entry represents.
         */
        protected int node;
        /**
         * The Total Cost value to get to the node.
         */
        protected double f;
        /**
         * The accumulated path cost to get to that node.
         */
        protected double g;
        /**
         * The Eucidean Component value to get to the node.
         */
        protected double h;
        protected AStarEntry parent;

        public AStarEntry(int node, double f, double g, double h, AStarEntry parent) {
            this.node = node;
            this.f = f;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        public double getF() {
            return f;
        }

        public void setF(double f) {
            this.f = f;
        }

        public double getG() {
            return g;
        }

        public void setG(double g) {
            this.g = g;
        }

        public double getH() {
            return h;
        }

        public void setH(double h) {
            this.h = h;
        }

        public AStarEntry getParent() {
            return parent;
        }

        public void setParent(AStarEntry parent) {
            this.parent = parent;
        }
    }

}
