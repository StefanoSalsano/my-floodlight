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
import org.zoolu.tools.BinTools;
import java.io.IOException;



/** TcpMsgTransportConnection provides a TCP-based message oriented trasport service.
  */
class TcpMsgTransportConnection implements MsgTransportConnection, TcpConnectionListener
{
   /** TCP protocol type */
   static final String PROTO_TCP="tcp";

   /** Message parser */
   MessageParser parser;  

   /** TCP connection */
   TcpConnection tcp_conn;  

   /** The last time that has been used (in milliseconds) */
   long last_time;
   
   /** The data buffer */
   byte[] data_buffer;
     
   /** The data offset */
   int data_offset;

   /** The data length */
   int data_length;

   /** MsgTransportConnection listener */
   MsgTransportConnectionListener listener;   



   /** Creates a new TcpMsgTransportConnection. */ 
   public TcpMsgTransportConnection(SocketAddress remote_soaddr, MessageParser parser, MsgTransportConnectionListener listener) throws IOException
   {  this.parser=parser;
      this.listener=listener;
      TcpSocket socket=new TcpSocket(remote_soaddr.getAddress(),remote_soaddr.getPort());
      tcp_conn=new TcpConnection(socket,this);
      last_time=System.currentTimeMillis();
   }


   /** Creates a new TcpMsgTransportConnection. */
   public TcpMsgTransportConnection(TcpSocket socket, MessageParser parser, MsgTransportConnectionListener listener)
   {  this.parser=parser;
      this.listener=listener;
      tcp_conn=new TcpConnection(socket,this);
      last_time=System.currentTimeMillis();
   }


   /** From MsgTransportConnection. Gets protocol type. */ 
   public String getProtocol()
   {  return PROTO_TCP;
   }


   /** From MsgTransportConnection. Gets the remote socket address. */
   public SocketAddress getRemoteSocketAddress()
   {  if (tcp_conn!=null) return new SocketAddress(tcp_conn.getRemoteAddress(),tcp_conn.getRemotePort());
      else return null;
   }
   
   
   /** From MsgTransportConnection. Gets the last time the Connection has been used (in millisconds). */
   public long getLastTimeMillis()
   {  return last_time;
   }


   /** From MsgTransportConnection. Sends a Message. */      
   public void sendMessage(Message msg) throws IOException
   {  if (tcp_conn!=null)
      {  last_time=System.currentTimeMillis();
         byte[] data=msg.getBytes();
         tcp_conn.send(data);
         //System.out.println("DEBUG: TcpMsgTransportConnection: message sent: "+msg.getLength());
      }
   }


   /** From MsgTransportConnection. Stops running. */
   public void halt()
   {  if (tcp_conn!=null) tcp_conn.halt();
   }


   /** From MsgTransportConnection. Gets a String representation of this object. */
   public String toString()
   {  if (tcp_conn!=null) return tcp_conn.toString();
      else return null;
   }


   //************************* Callback methods *************************
   
   /** When new data is received through the TcpConnection. */
   public void onReceivedData(TcpConnection tcp_conn, byte[] data, int len)
   {  last_time=System.currentTimeMillis();
      
      parser.append(data,0,len);
      Message msg=parser.parseMessage();
      while (msg!=null)
      {  //System.out.println("DEBUG: TcpMsgTransportConnection: message received: "+msg.getLength());
         msg.setRemoteSocketAddress(new SocketAddress(tcp_conn.getRemoteAddress(),tcp_conn.getRemotePort()));
         msg.setMsgTransportConnection(this);
         if (listener!=null) listener.onReceivedMessage(this,msg);
         msg=parser.parseMessage();
      }     
   }   


   /** When TcpConnection terminates. */
   public void onConnectionTerminated(TcpConnection tcp_conn, Exception error)  
   {  if (listener!=null) listener.onConnectionTerminated(this,error);
      TcpSocket socket=tcp_conn.getSocket();
      if (socket!=null) try { socket.close(); } catch (Exception e) {}
      this.tcp_conn=null;
      this.listener=null;
   }

}
