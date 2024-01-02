package PeersimSimulator.peersim.env.Links;

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
    protected List<Integer> indexInLinkableToId;


// --------------------------------------------------------------------------
// Initialization
// --------------------------------------------------------------------------

    public TableProtocol(String s)
    {
        int initCapacity =Configuration.getInt(s + "." + PAR_INITCAP,
                DEFAULT_INITIAL_CAPACITY);
        neighbors = new HashMap<>(initCapacity);
        indexInLinkableToId = new ArrayList<>(initCapacity);
    }

//--------------------------------------------------------------------------

    public Object clone()
    {
        TableProtocol ip = null;
        try { ip = (TableProtocol) super.clone(); }
        catch( CloneNotSupportedException e ) {} // never happens
        ip.neighbors = new HashMap<>(neighbors.size());
        indexInLinkableToId = new ArrayList<>(neighbors.size());

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
        this.indexInLinkableToId.add((int) n.getID()); // 1 0
        return this.neighbors.put((int) n.getID(), n) == null; // (1,n1) (0, n0)
    }

// --------------------------------------------------------------------------

    // TODO: This class is relly just extra steps for no reason. I added it when I was thinking of maintaining the option
    //  of having the getNeighbour work by the node id instead of position in linkable, but that did not happen. We use
    //  the position in linkable.
    public Node getNeighbor(int i)
    {
        return neighbors.get(indexInLinkableToId.get(i));
    }

// --------------------------------------------------------------------------

    public int degree()
    {
        return this.indexInLinkableToId.size();
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
        for (Integer i : indexInLinkableToId) {
            Node n = neighbors.get(i);
            buffer.append(n.getIndex()).append(" ");
        }

        return buffer.append("]").toString();
    }

// --------------------------------------------------------------------------

    public void onKill()
    {
        neighbors = null;
        indexInLinkableToId = null;
    }

}
