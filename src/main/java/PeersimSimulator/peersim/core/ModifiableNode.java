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

package PeersimSimulator.peersim.core;


/**
 * This class extends GeneralNode by allowing to modify single
 * protocol instances at nodes.
 *
 * @author Alberto Montresor
 * @version $Revision: 1.3 $
 */
public class ModifiableNode extends GeneralNode
{

/**
 * Invokes the super constructor.
 */
public ModifiableNode(String prefix)
{
	super(prefix);
}

/**
 * Substitutes the specified protocol at this node.
 * 
 * @param pid protocol identifier
 * @param prot the protocol object
 */
public void setProtocol(int pid, Protocol prot)
{
	protocol[pid] = prot;
}

}
