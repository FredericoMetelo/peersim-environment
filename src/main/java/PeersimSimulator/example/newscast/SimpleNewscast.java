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

package PeersimSimulator.example.newscast;

import PeersimSimulator.peersim.cdsim.*;
import PeersimSimulator.peersim.config.*;
import PeersimSimulator.peersim.core.*;

/**
 * This class represents the information stored by a node in the simplified
 * newscast system (i.e., used just as a topology manager)
 */
public class SimpleNewscast implements CDProtocol, Linkable
{

// =============== static fields =======================================
// =====================================================================

// We are using static temporary arrays to avoid garbage collection
// of them. these are used by all SimpleNewscast protocols included
// in the protocol array so its size is the maximum of the cache sizes

/** Temp array for merging. Its size is the same as the cache size. */
private static Node[] tn;

/** Temp array for merging. Its size is the same as the cache size. */
private static int[] ts;

/**
 * Cache size.
 * @config
 */
private static final String PAR_CACHE = "cache";

// =================== fields ==========================================
// =====================================================================

/** Neighbors currently in the cache */
private Node[] cache;

/** Time stamps currently in the cache */
private int[] tstamps;

// ====================== initialization ===============================
// =====================================================================

public SimpleNewscast(String n)
{

	final int cachesize = Configuration.getInt(n + "." + PAR_CACHE);
	if (SimpleNewscast.tn == null || SimpleNewscast.tn.length < cachesize) {
		SimpleNewscast.tn = new Node[cachesize];
		SimpleNewscast.ts = new int[cachesize];
	}

	cache = new Node[cachesize];
	tstamps = new int[cachesize];
}

// ---------------------------------------------------------------------

public Object clone()
{

	SimpleNewscast sn = null;
	try { sn = (SimpleNewscast) super.clone(); }
	catch( CloneNotSupportedException e ) {} // never happens
	sn.cache = new Node[cache.length];
	sn.tstamps = new int[tstamps.length];
	System.arraycopy(cache, 0, sn.cache, 0, cache.length);
	System.arraycopy(tstamps, 0, sn.tstamps, 0, tstamps.length);
	return sn;
}

// ====================== helper methods ==============================
// ====================================================================

/**
 * Returns a peer node which is accessible (has ok fail state). This
 * implementation starts with a random node. If that is not reachable,
 * proceed first towards the older and then younger nodes until the first
 * reachable is found.
 * @return null if no accessible peers are found, the peer otherwise.
 */
private Node getPeer()
{

	final int d = degree();
	if (d == 0)
		return null;
	int index = CommonState.r.nextInt(d);
	Node result = cache[index];

	if (result.isUp())
		return result;

	// proceed towards older entries
	for (int i = index + 1; i < d; ++i)
		if (cache[i].isUp())
			return cache[i];

	// proceed towards younger entries
	for (int i = index - 1; i >= 0; --i)
		if (cache[i].isUp())
			return cache[i];

	// no accessible peer
	return null;
}

// --------------------------------------------------------------------

/**
 * Merge the content of two nodes and adds a new version of the identifier.
 * The result is in the static temporary arrays. The first element is not
 * defined, it is reserved for the freshest new updates so it will be
 * different for peer and this. The elements of the static temporary arrays
 * will not contain neither peerNode nor thisNode.
 * @param thisNode
 *          the node that hosts this newscast protocol instance (process)
 * @param peer
 *          The peer with which we perform cache exchange
 * @param peerNode
 *          the node that hosts the peer newscast protocol instance
 */
private void merge(Node thisNode, SimpleNewscast peer, Node peerNode )
{
	int i1 = 0; /* Index first cache */
	int i2 = 0; /* Index second cache */
	boolean first;
	boolean lastTieWinner = CommonState.r.nextBoolean();
	int i = 1; // Index new cache. first element set in the end
	// SimpleNewscast.tn[0] is always null. it's never written anywhere
	final int d1 = degree();
	final int d2 = peer.degree();
	// cachesize is cache.length

	// merging two arrays
	while (i < cache.length && i1 < d1 && i2 < d2) {
		if (tstamps[i1] == peer.tstamps[i2]) {
			lastTieWinner = first = !lastTieWinner;
		} else {
			first = tstamps[i1] > peer.tstamps[i2];
		}

		if (first) {
			if (cache[i1] != peerNode && !SimpleNewscast.contains(i, cache[i1])) {
				SimpleNewscast.tn[i] = cache[i1];
				SimpleNewscast.ts[i] = tstamps[i1];
				i++;
			}
			i1++;
		} else {
			if (peer.cache[i2] != thisNode
					&& !SimpleNewscast.contains(i, peer.cache[i2])) {
				SimpleNewscast.tn[i] = peer.cache[i2];
				SimpleNewscast.ts[i] = peer.tstamps[i2];
				i++;
			}
			i2++;
		}
	}

	// if one of the original arrays got fully copied into
	// tn and there is still place, fill the rest with the other
	// array
	if (i < cache.length) {
		// only one of the for cycles will be entered

		for (; i1 < d1 && i < cache.length; ++i1) {
			if (cache[i1] != peerNode && !SimpleNewscast.contains(i, cache[i1])) {
				SimpleNewscast.tn[i] = cache[i1];
				SimpleNewscast.ts[i] = tstamps[i1];
				i++;
			}
		}

		for (; i2 < d2 && i < cache.length; ++i2) {
			if (peer.cache[i2] != thisNode
					&& !SimpleNewscast.contains(i, peer.cache[i2])) {
				SimpleNewscast.tn[i] = peer.cache[i2];
				SimpleNewscast.ts[i] = peer.tstamps[i2];
				i++;
			}
		}
	}

	// if the two arrays were not enough to fill the buffer
	// fill in the rest with nulls
	if (i < cache.length) {
		for (; i < cache.length; ++i) {
			SimpleNewscast.tn[i] = null;
		}
	}
}

// --------------------------------------------------------------------

private static boolean contains(int size, Node peer)
{
	for (int i = 0; i < size; i++) {
		if (SimpleNewscast.tn[i] == peer)
			return true;
	}
	return false;
}

// ====================== Linkable implementation =====================
// ====================================================================

/**
 * Does not check if the index is out of bound (larger than
 * {@link #degree()})
 */
public Node getNeighbor(int i)
{

	return cache[i];
}

// --------------------------------------------------------------------

/** Might be less than cache size. */
public int degree()
{

	int len = cache.length - 1;
	while (len >= 0 && cache[len] == null)
		len--;
	return len + 1;
}

// --------------------------------------------------------------------

public boolean addNeighbor(Node node)
{

	int i;
	for (i = 0; i < cache.length && cache[i] != null; i++) {
		if (cache[i] == node)
			return false;
	}

	if (i < cache.length) {
		if (i > 0 && tstamps[i - 1] < CommonState.getIntTime()) {
			// we need to insert to the first position
			for (int j = cache.length - 2; j >= 0; --j) {
				cache[j + 1] = cache[j];
				tstamps[j + 1] = tstamps[j];
			}
			i = 0;
		}
		cache[i] = node;
		tstamps[i] = CommonState.getIntTime();
		return true;
	} else
		throw new IndexOutOfBoundsException();
}

// --------------------------------------------------------------------

public void pack()
{
}

// --------------------------------------------------------------------

public boolean contains(Node n)
{
	for (int i = 0; i < cache.length; i++) {
		if (cache[i] == n)
			return true;
	}
	return false;
}

// --------------------------------------------------------------------

public void onKill()
{
	cache = null;
	tstamps = null;
}

// ===================== CDProtocol implementations ===================
// ====================================================================

public void nextCycle(Node n, int protocolID)
{
	Node peerNode = getPeer();
	if (peerNode == null) {
		System.err.println("Newscast: no accessible peer");
		return;
	}

	SimpleNewscast peer = (SimpleNewscast) (peerNode.getProtocol(protocolID));
	merge(n, peer, peerNode);

	// set new cache in this and peer
	System.arraycopy(SimpleNewscast.tn, 0, cache, 0, cache.length);
	System.arraycopy(SimpleNewscast.ts, 0, tstamps, 0, tstamps.length);
	System.arraycopy(SimpleNewscast.tn, 0, peer.cache, 0, cache.length);
	System.arraycopy(SimpleNewscast.ts, 0, peer.tstamps, 0, tstamps.length);

	// set first element
	tstamps[0] = peer.tstamps[0] = CommonState.getIntTime();
	cache[0] = peerNode;
	peer.cache[0] = n;
}

// ===================== other public methods =========================
// ====================================================================

public String toString()
{
	if( cache == null ) return "DEAD!";
	
	StringBuffer sb = new StringBuffer();

	for (int i = 0; i < degree(); ++i) {
		sb.append(" (" + cache[i].getIndex() + "," + tstamps[i] + ")");
	}
	return sb.toString();
}

}
