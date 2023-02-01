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

package PeersimSimulator.peersim.reports;

import PeersimSimulator.peersim.core.*;

/**
 * This observer reports memory utilization (max, total and 
 * free, as defined by <code>java.lang.Runtime</code>).
 *
 * @author Alberto Montresor
 * @version $Revision: 1.1 $
 */
public class MemoryObserver implements Control
{

/** The runtime object to obtain memory info */
private final static Runtime r = Runtime.getRuntime(); 

/** The prefix to be printed */
private final String prefix;

/**
 * Constructor to be instantiated in PeerSim.
 * @param prefix
 */
public MemoryObserver(String prefix)
{
	this.prefix = prefix;
}

public boolean execute()
{
	System.out.println(prefix + ": max=" + r.maxMemory() + ", total=" + 
			r.totalMemory() + ", free=" + r.freeMemory()); 
	return false;
}

}
