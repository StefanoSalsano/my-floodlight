package local.conet;



import org.zoolu.net.SocketAddress;
import org.zoolu.net.message.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.Iterator;



/** Server that receives and responds to JSON-based text messages.
  */
public class JsonMessageServer implements MsgTransportListener
{

   /** Creates a new server. */
   public JsonMessageServer(int port)
   {  try
      {  TcpMsgTransport tcp=new TcpMsgTransport(port,10,new JsonMessageParser());
         tcp.setListener(this);
      }
      catch (java.io.IOException e)
      {  e.printStackTrace();
      }
   }


   /** From MsgTransportListener. When a new message is received. */
   public void onReceivedMessage(MsgTransport transport, Message msg)
   {  try
      {  JSONObject json_message=(JSONObject)JSONValue.parse(msg.toString());
         System.out.println("Json message: "+json_message.toString());
         for (Iterator i=json_message.keySet().iterator(); i.hasNext(); )
         {  Object obj=i.next();
            System.out.println("Json obj: "+obj+"="+json_message.get(obj));
         }
         System.out.println("Json message type: "+json_message.get("type"));
      }
      catch (Exception e)
      {  System.out.println("Received message: "+msg);
         e.printStackTrace();
      }
   }


   /** From MsgTransportListener. When the transport service terminates. */
   public void onMsgTransportTerminated(MsgTransport transport, Exception error)
   {
   }



   /** Main method. */
   public static void main(String[] args)
   {  try
      {  int port=Integer.parseInt(args[0]);
         new JsonMessageServer(port);
      }
      catch (Exception e)
      {  e.printStackTrace();
      }
   }
   
}

