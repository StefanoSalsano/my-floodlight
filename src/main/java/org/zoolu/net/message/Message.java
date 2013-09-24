/*
 * Copyright (C) 2012 Luca Veltri - University of Parma - Italy
 * 
 * This file is part of MjSip (http://www.mjsip.org)
 * 
 * MjSip is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * MjSip is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MjSip; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Author(s):
 * Luca Veltri (luca.veltri@unipr.it)
 */

package org.zoolu.net.message;



import org.zoolu.net.SocketAddress;



/** Message is a generic transport message.
  */
public interface Message
{
   /** Sets the remote socket address. */ 
   public void setRemoteSocketAddress(SocketAddress soaddr);


   /** Gets the remote socket address. */ 
   public SocketAddress getRemoteSocketAddress();


   /** Sets the transport protocol. */ 
   public void setTransportProtocol(String proto);


   /** Gets the transport protocol. */ 
   public String getTransportProtocol();


   /** Sets the transport connection. */ 
   public void setMsgTransportConnection(MsgTransportConnection conn);


   /** Gets the transport connection. */ 
   public MsgTransportConnection getMsgTransportConnection();


   /** Gets bytes. */
   public byte[] getBytes();


   /** Gets a String representation of this Object. */
   public String toString();

}
