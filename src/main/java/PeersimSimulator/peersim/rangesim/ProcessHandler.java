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

package PeersimSimulator.peersim.rangesim;

public interface ProcessHandler
{

/**
 * Stop the external process managed by this object.
 */
public abstract void doStop();

/**
 * Wait until the process has terminated. 
 * @exception InterruptedException 
 *   thrown if the wait is interrupted
 */
public abstract void join()
  throws InterruptedException;

}