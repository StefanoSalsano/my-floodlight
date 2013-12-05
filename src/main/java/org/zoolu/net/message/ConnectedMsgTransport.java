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
import org.zoolu.tools.*;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.IOException;



/** ConnectedMsgTransport is a generic Connection Oriented (CO)
  * message-oriented transport service.
  */
abstract class ConnectedMsgTransport implements MsgTransport, TcpServerListener, MsgTransportConnectionListener
{        
   /** Debug level */
   public static int DEBUG=2;


   /** Max number of (contemporary) open connections */
   int nmax_connections=0;

   /** Table of active connections (Hashtable<SocketAddress,MsgTransportConnection>) */
   public Hashtable connections=null;

   /** MsgTransport listener */
   protected MsgTransportListener listener;   

   /** Message parser */
   protected MessageParser parser;  




   /** Creates a new ConnectedMsgTransport */ 
   public ConnectedMsgTransport(int local_port, int nmax_connections, MessageParser parser) throws IOException
   {  this.nmax_connections=nmax_connections;
      this.parser=parser;
      connections=new Hashtable();
   }


   /** Creates a proper transport connection to the remote socket address. */
   abstract protected MsgTransportConnection createMsgTransportConnection(SocketAddress remote_soaddr)  throws IOException;


   /** From MsgTransport. Sets transport listener */
   public void setListener(MsgTransportListener listener)
   {  this.listener=listener;
   }


   /** From MsgTransport. Sends a Message.
     * If an active connection is found for the same remote address, such connection is used;
     * otherwise the message is sent through a new connection. */      
   public MsgTransportConnection sendMessage(Message msg) throws IOException
   {  SocketAddress dest_soaddr=msg.getRemoteSocketAddress();
      if (!connections.containsKey(dest_soaddr))
      {  printLog("no active connection found matching remote address "+dest_soaddr,LOG_LEVEL_MEDIUM);
         printLog("open "+getProtocol()+" connection to "+dest_soaddr,LOG_LEVEL_MEDIUM);
         MsgTransportConnection conn=null;
         try
         {  conn=createMsgTransportConnection(dest_soaddr);
         }
         catch (Exception e)
         {  printLog("connection setup FAILED",LOG_LEVEL_HIGH);
            return null;
         }
         printLog("connection "+conn+" opened",LOG_LEVEL_HIGH);
         addConnection(conn);
      }
      else
      {  printLog("found an already active connection to "+dest_soaddr,LOG_LEVEL_MEDIUM);
      }
      MsgTransportConnection conn=(MsgTransportConnection)connections.get(dest_soaddr);
      if (conn!=null)
      {  printLog("sending data through conn "+conn,LOG_LEVEL_MEDIUM);
         try
         {  conn.sendMessage(msg);
            return conn;
         }
         catch (IOException e)
         {  printException(e,LOG_LEVEL_HIGH);
            return null;
         }
      }
      else
      {  // this point has not to be reached
         printLog("ERROR: no conn to "+dest_soaddr+" found: message discarded.",LOG_LEVEL_MEDIUM);
         return null;
      }
   }


   /** Sends the message <i>msg</i> using the given connection. */
   public MsgTransportConnection sendMessage(Message msg, MsgTransportConnection conn)
   {  if (conn!=null && conn==connections.get(conn.getRemoteSocketAddress()))
      {  // connection exists
         printLog("active connection "+conn+" found",LOG_LEVEL_MEDIUM);
         try
         {  conn.sendMessage(msg);
            return conn;
         }
         catch (Exception e)
         {  printException(e,LOG_LEVEL_HIGH);
         }
      }
      //else
      printLog("no active connection found: message discarded.",LOG_LEVEL_MEDIUM);
      return null;
   }


   /** From MsgTransport. Stops running */
   public void halt()
   {  // close all connections
      if (connections!=null)
      {  printLog("connections are going down",LOG_LEVEL_LOW);
         for (Enumeration e=connections.elements(); e.hasMoreElements(); )
         {  MsgTransportConnection c=(MsgTransportConnection)e.nextElement();
            c.halt();
         }
         connections=null;
      }
   }


   /** From TcpServerListener. When a new incoming connection is established */ 
   abstract public void onIncomingConnection(TcpServer tcp_server, TcpSocket socket);



   /** From TcpServerListener. When TcpServer terminates. */
   public void onServerTerminated(TcpServer tcp_server, Exception error) 
   {  printLog("tcp server "+tcp_server+" terminated",LOG_LEVEL_MEDIUM);
   }


   /** From MsgTransportConnectionListener. When a new SIP message is received. */
   public void onReceivedMessage(MsgTransportConnection conn, Message msg)
   {  if (listener!=null) listener.onReceivedMessage(this,msg);
   }
   

   /** From MsgTransportConnectionListener. When MsgTransportConnection terminates. */
   public void onConnectionTerminated(MsgTransportConnection conn, Exception error)
   {  removeConnection(conn);
      if (error!=null) printException(error,LOG_LEVEL_HIGH);
   }


   /** Adds a new Connection */ 
   protected synchronized void addConnection(MsgTransportConnection conn)
   {  SocketAddress conn_soaddr=conn.getRemoteSocketAddress();
      if (connections.containsKey(conn_soaddr))
      {  // remove the previous connection
         printLog("trying to add the already established connection to "+conn_soaddr,LOG_LEVEL_HIGH);
         printLog("connection to "+conn_soaddr+" will be replaced",LOG_LEVEL_HIGH);
         MsgTransportConnection old_conn=(MsgTransportConnection)connections.get(conn_soaddr);
         old_conn.halt();
         connections.remove(conn_soaddr);
      }
      if (connections.size()>=nmax_connections)
      {  // remove the older unused connection
         printLog("reached the maximum number of connection: removing the older unused connection",LOG_LEVEL_HIGH);
         long older_time=System.currentTimeMillis();
         MsgTransportConnection older_conn=null;
         for (Enumeration e=connections.elements(); e.hasMoreElements(); )
         {  MsgTransportConnection e_conn=(MsgTransportConnection)e.nextElement();
            if (e_conn.getLastTimeMillis()<older_time)
            {  older_conn=e_conn;
               older_time=older_conn.getLastTimeMillis();
            }
         }
         if (older_conn!=null) removeConnection(older_conn);
      }
      connections.put(conn_soaddr,conn);
      // DEBUG:
      printLog("active connenctions:",LOG_LEVEL_LOW);
      for (Enumeration e=connections.keys(); e.hasMoreElements(); )
      {  SocketAddress e_conn_soaddr=(SocketAddress)e.nextElement();
         printLog("conn: "+((MsgTransportConnection)connections.get(e_conn_soaddr)).toString(),LOG_LEVEL_LOW);
      }
   }

 
   /** Removes a Connection. */ 
   protected void removeConnection(MsgTransportConnection conn)
   {  removeConnection(conn.getRemoteSocketAddress());
   }


   /** Removes a Connection. */ 
   protected synchronized void removeConnection(SocketAddress conn_soaddr)
   {  if (connections!=null && connections.containsKey(conn_soaddr))
      {  MsgTransportConnection conn=(MsgTransportConnection)connections.get(conn_soaddr);
         conn.halt();
         connections.remove(conn_soaddr);
         // DEBUG:
         printLog("active connenctions:",LOG_LEVEL_LOW);
         for (Enumeration e=connections.elements(); e.hasMoreElements(); )
         {  MsgTransportConnection e_conn=(MsgTransportConnection)e.nextElement();
            printLog("conn "+e_conn.toString(),LOG_LEVEL_LOW);
         }
      }
   }


   // ****************************** Logs *****************************

   /** Log level hight */
   static final int LOG_LEVEL_HIGH=1;

   /** Log level medium */
   static final int LOG_LEVEL_MEDIUM=2;

   /** Log level low */
   static final int LOG_LEVEL_LOW=3;


   /** Adds a new string to the default Log. */
   void printLog(String str, int level)
   {  if (DEBUG>=level) System.err.println(getProtocol()+": "+str);  
   }

   /** Prints an exception to the event Log. */
   void printException(Exception e, int level)
   {  printLog("Exception: "+ExceptionPrinter.getStackTraceOf(e),level);
   }

}
