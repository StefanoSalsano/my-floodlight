package org.zoolu.net.message;



import org.zoolu.net.SocketAddress;



public class StringMessage implements Message
{
   /** The actual message */
   protected String message;
   
   /** The transport protocol */
   protected String proto=null;

   /** The remote address */
   protected SocketAddress remote_soaddr=null;

   /** The transport connection associated with this message (if any) */
   protected MsgTransportConnection connection=null;




   /** Creates a new StringMessage. */ 
   public StringMessage(String message)
   {  this.message=message;
   }


   /** Creates a new StringMessage. */ 
   public StringMessage(StringMessage sm)
   {  this.message=sm.message;
      this.proto=sm.proto;
      this.remote_soaddr=sm.remote_soaddr;
      this.connection=sm.connection;
   }


   /** Creates and returns a copy of this object. */
   public Object clone()
   {  return new StringMessage(this);
   }


   /** Whether this object is equal to <i>obj</i>. */
   public boolean equals(Object obj)
   {  try
      {  StringMessage sm=(StringMessage)obj;
         if (sm.toString().equals(this.toString())) return true;
         else return false;
      }
      catch (Exception e) {  return false;  }
   }


   /** From Message. Sets the remote socket address. */ 
   public void setRemoteSocketAddress(SocketAddress soaddr)
   {  this.remote_soaddr=soaddr;
   }


   /** From Message. Gets the remote socket address. */ 
   public SocketAddress getRemoteSocketAddress()
   {  return remote_soaddr;
   }


   /** From Message. Sets the transport protocol. */ 
   public void setTransportProtocol(String proto)
   {  this.proto=proto;
   }


   /** From Message. Gets the transport protocol. */ 
   public String getTransportProtocol()
   {  return proto;
   }


   /** From Message. Sets the transport connection. */ 
   public void setMsgTransportConnection(MsgTransportConnection conn)
   {  this.connection=conn;
   }


   /** From Message. Gets the transport connection. */ 
   public MsgTransportConnection getMsgTransportConnection()
   {  return connection;
   }


   /** Gets bytes. */
   public byte[] getBytes()
   {  if (message!=null) return message.getBytes();
      else return null;
   }


   /** Gets a String representation of this Object. */
   public String toString()
   {  return message;
   }
   
}