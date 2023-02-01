/*
 * Copyright (c) 2003 The BISON Project
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

package PeersimSimulator.example.edaggregation;

import PeersimSimulator.peersim.vector.SingleValueHolder;
import PeersimSimulator.peersim.config.*;
import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.transport.Transport;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.edsim.EDProtocol;

/**
* Event driven version of epidemic averaging.
*/
public class AverageED extends SingleValueHolder
implements CDProtocol, EDProtocol {

//--------------------------------------------------------------------------
// Initialization
//--------------------------------------------------------------------------

/**
 * @param prefix string prefix for config properties
 */
public AverageED(String prefix) { super(prefix); }


//--------------------------------------------------------------------------
// methods
//--------------------------------------------------------------------------

/**
 * This is the standard method the define periodic activity.
 * The frequency of execution of this method is defined by a
 * {@link PeersimSimulator.peersim.edsim.CDScheduler} component in the configuration.
 */
public void nextCycle( Node node, int pid )
{
	Linkable linkable = 
		(Linkable) node.getProtocol( FastConfig.getLinkable(pid) );
	if (linkable.degree() > 0)
	{
		Node peern = linkable.getNeighbor(
					CommonState.r.nextInt(linkable.degree()
				)
		);
		
		// XXX quick and dirty handling of failures
		// (message would be lost anyway, we save time)
		if(!peern.isUp()) return;
		
		((Transport)node.getProtocol(FastConfig.getTransport(pid))).
			send(
				node,
				peern,
				new AverageMessage(value,node),
				pid);
	}
}

//--------------------------------------------------------------------------

/**
* This is the standard method to define to process incoming messages.
*/
public void processEvent( Node node, int pid, Object event ) {
		
	AverageMessage aem = (AverageMessage)event;
	
	if( aem.sender!=null )
		((Transport)node.getProtocol(FastConfig.getTransport(pid))).
			send(
				node,
				aem.sender,
				new AverageMessage(value,null),
				pid);
				
	value = (value + aem.value) / 2;
}

}

//--------------------------------------------------------------------------
//--------------------------------------------------------------------------

/**
* The type of a message. It contains a value of type double and the
* sender node of type {@link PeersimSimulator.peersim.core.Node}.
*/
class AverageMessage {

	final double value;
	/** If not null,
	this has to be answered, otherwise this is the answer. */
	final Node sender;
	public AverageMessage( double value, Node sender )
	{
		this.value = value;
		this.sender = sender;
	}
}

