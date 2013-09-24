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



/** TlsMsgTransport provides a TLS-based transport service for SIP.
  */
public class TlsMsgTransport extends ConnectedMsgTransport
{        
   /** TLS protocol type */
   public static final String PROTO_TLS="tls";
   
   /** TLS server */
   TcpServer tls_server=null;

   /** TLS socket factory */
   TlsSocketFactory tls_socket_factory=null;




   /** Creates a new TlsMsgTransport */ 
   public TlsMsgTransport(int local_port, int nmax_connections, String key_file, String cert_file, String trust_folder, boolean trust_all, MessageParser parser) throws IOException
   {  super(local_port,nmax_connections,parser);
      initTls(local_port,null,key_file,cert_file,trust_folder,trust_all);
   }


   /** Creates a new TlsMsgTransport */ 
   public TlsMsgTransport(int local_port, IpAddress host_ipaddr, int nmax_connections, String key_file, String cert_file, String trust_folder, boolean trust_all, MessageParser parser) throws IOException
   {  super(local_port,nmax_connections,parser);
      initTls(local_port,host_ipaddr,key_file,cert_file,trust_folder,trust_all);
   }


   /** Inits the TlsMsgTransport */ 
   protected void initTls(int local_port, IpAddress host_ipaddr, String key_file, String cert_file, String trust_folder, boolean trust_all) throws IOException
   {  if (tls_server!=null) tls_server.halt();
      // start tls
      try
      {  TlsContext tls_context=new TlsContext();
         tls_context.setKeyCert(key_file,cert_file);
         if (trust_all) tls_context.setTrustAll(true);
         else tls_context.addTrustFolder(trust_folder);
         // tls server
         TlsServerFactory tls_server_factory=new TlsServerFactory(tls_context);
         if (host_ipaddr==null) tls_server=tls_server_factory.createTlsServer(local_port,this);
         else tls_server=tls_server_factory.createTlsServer(local_port,host_ipaddr,this);
         // tls client
         tls_socket_factory=new TlsSocketFactory(tls_context);
         // force the newest TLS version
         //String[] ep=tls_socket_factory.getEnabledProtocols();
         //String[] sp={ ep[ep.length-1] };
         //tls_socket_factory.setEnabledProtocols(sp);
         //System.err.println("DEBUG: TlsMsgTransport: enabled protocols: "+sp[0]);
      }
      catch (Exception e)
      {  e.printStackTrace();
         throw new IOException(e.getMessage());
      }
   }


   /** Gets protocol type */ 
   public String getProtocol()
   {  return PROTO_TLS;
   }


   /** Gets local port */ 
   public int getLocalPort()
   {  if (tls_server!=null) return tls_server.getPort();
      else return 0;
   }


   /** Stops running */
   public void halt()
   {  super.halt();
      if (tls_server!=null) tls_server.halt();
   }


   /** From TcpServerListener. When a new incoming connection is established */ 
   public void onIncomingConnection(TcpServer tcp_server, TcpSocket socket)
   {  printLog("incoming connection from "+socket.getAddress()+":"+socket.getPort(),LOG_LEVEL_MEDIUM);
      if (tcp_server==this.tls_server)
      {  MsgTransportConnection conn=new TlsMsgTransportConnection(socket,parser,this);
         printLog("tls connection "+conn+" opened",LOG_LEVEL_MEDIUM);
         addConnection(conn);
      }
   }


   /** From ConnectedMsgTransport. Creates a transport connection to the remote socket. */
   protected MsgTransportConnection createMsgTransportConnection(SocketAddress remote_soaddr) throws IOException
   {  TcpSocket tls_socket=tls_socket_factory.createTlsSocket(remote_soaddr.getAddress(),remote_soaddr.getPort());
      return new TlsMsgTransportConnection(tls_socket,parser,this);
   }


   /** Gets a String representation of this object. */
   public String toString()
   {  if (tls_server!=null) return tls_server.toString();
      else return null;
   }
}
