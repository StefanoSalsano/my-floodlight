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



import org.zoolu.net.*;
import java.io.IOException;



/** TcpMsgTransport provides a message-oriented transport service based on TCP.
  */
public class TcpMsgTransport extends ConnectedMsgTransport
{        
   /** TCP protocol type */
   public static final String PROTO_TCP="tcp";
   
   /** TCP server */
   TcpServer tcp_server=null;




   /** Creates a new TcpMsgTransport */ 
   public TcpMsgTransport(int local_port, int nmax_connections, MessageParser parser) throws IOException
   {  super(local_port,nmax_connections,parser);
      initTcp(local_port,null);
   }


   /** Creates a new TcpMsgTransport */ 
   public TcpMsgTransport(int local_port, IpAddress host_ipaddr, int nmax_connections, MessageParser parser) throws IOException
   {  super(local_port,nmax_connections,parser);
      initTcp(local_port,host_ipaddr);
   }


   /** Inits the TcpMsgTransport */ 
   protected void initTcp(int local_port, IpAddress host_ipaddr) throws IOException
   {  if (tcp_server!=null) tcp_server.halt();
      // start tcp
      if (host_ipaddr==null) tcp_server=new TcpServer(local_port,this);
      else tcp_server=new TcpServer(local_port,host_ipaddr,this);
   }


   /** From MsgTransport. Gets protocol type */ 
   public String getProtocol()
   {  return PROTO_TCP;
   }


   /** From MsgTransport. Gets local port */ 
   public int getLocalPort()
   {  if (tcp_server!=null) return tcp_server.getPort();
      else return 0;
   }


   /** From MsgTransport. Stops running. */
   public void halt()
   {  super.halt();
      if (tcp_server!=null) tcp_server.halt();
   }


   /** From TcpServerListener. When a new incoming connection is established */ 
   public void onIncomingConnection(TcpServer tcp_server, TcpSocket socket)
   {  printLog("incoming connection from "+socket.getAddress()+":"+socket.getPort(),LOG_LEVEL_MEDIUM);
      if (tcp_server==this.tcp_server)
      {  MsgTransportConnection conn=new TcpMsgTransportConnection(socket,parser,this);
         printLog("tcp connection "+conn+" opened",LOG_LEVEL_MEDIUM);
         addConnection(conn);
      }
   }


   /** From ConnectedMsgTransport. Creates a proper transport connection to the remote socket address. */
   protected MsgTransportConnection createMsgTransportConnection(SocketAddress remote_soaddr) throws IOException
   {  TcpSocket tcp_socket=new TcpSocket(remote_soaddr.getAddress(),remote_soaddr.getPort());
      return new TcpMsgTransportConnection(tcp_socket,parser,this);
   }


   /** From MsgTransport. Gets a String representation of this object */
   public String toString()
   {  if (tcp_server!=null) return tcp_server.toString();
      else return null;
   }

}
