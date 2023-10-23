package PeersimSimulator.peersim.SDN.Links;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.core.Protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableProtocol implements Protocol, Linkable
{

// --------------------------------------------------------------------------
// Parameters
// --------------------------------------------------------------------------

    /**
     * Default init capacity
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 10;

    /**
     * Initial capacity. Defaults to {@value #DEFAULT_INITIAL_CAPACITY}.
     * @config
     */
    private static final String PAR_INITCAP = "capacity";

// --------------------------------------------------------------------------
// Fields
// --------------------------------------------------------------------------

    /** Neighbors */
    protected Map<Integer, Node> neighbors;
    protected List<Integer> idPerInteger;


// --------------------------------------------------------------------------
// Initialization
// --------------------------------------------------------------------------

    public TableProtocol(String s)
    {
        int initCapacity =Configuration.getInt(s + "." + PAR_INITCAP,
                DEFAULT_INITIAL_CAPACITY);
        neighbors = new HashMap<>(initCapacity);
        idPerInteger = new ArrayList<>(initCapacity);
    }

//--------------------------------------------------------------------------

    public Object clone()
    {
        TableProtocol ip = null;
        try { ip = (TableProtocol) super.clone(); }
        catch( CloneNotSupportedException e ) {} // never happens
        ip.neighbors = new HashMap<>(neighbors.size());
        idPerInteger = new ArrayList<>(neighbors.size());

        return ip;
    }

// --------------------------------------------------------------------------
// Methods
// --------------------------------------------------------------------------

    public boolean contains(Node n)
    {

        return this.neighbors.containsKey((int) n.getID());
    }

// --------------------------------------------------------------------------

    /** Adds given node if it is not already in the network. There is no limit
     * to the number of nodes that can be added. */
    public boolean addNeighbor(Node n)
    {
        if(this.contains(n)) {
            return false;
        }
        this.idPerInteger.add((int) n.getID());
        boolean added = this.neighbors.put((int) n.getID(), n) == null;
        return added;
    }

// --------------------------------------------------------------------------

    public Node getNeighbor(int i)
    {

        return neighbors.get(idPerInteger.get(i));
    }

// --------------------------------------------------------------------------

    public int degree()
    {
        return this.idPerInteger.size();
    }

// --------------------------------------------------------------------------

    public void pack()
    {
        // We use a table now, so no need to pack

    }

// --------------------------------------------------------------------------

    public String toString()
    {
        if( neighbors == null ) return "DEAD!";
        StringBuilder buffer = new StringBuilder();
        buffer.append("len=").append(this.neighbors.size()).append(" [");
        for (Node n : neighbors.values()) {

        buffer.append(n.getIndex()).append(" ");
    }

        return buffer.append("]").toString();
    }

// --------------------------------------------------------------------------

    public void onKill()
    {
        neighbors = null;
        idPerInteger = null;
    }

}
