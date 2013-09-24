package local.conet;



import org.zoolu.net.SocketAddress;
import org.zoolu.net.message.*;

import org.json.simple.JSONObject;
import java.io.*;



public class JsonMessageClient
{


   public static void main(String[] args)
   {
      try
      {  if (args.length<3)
         {  System.out.println("\nUsage: java JsonMessageClient <mac_addr> <ip_addr> <server_soaddr>\n");
            System.exit(0);
         }
         // else
         String host_macaddr=args[0];
         String host_ipaddr=args[1];
         SocketAddress remote_soaddr=new SocketAddress(args[2]);
       
         TcpMsgTransport tcp=new TcpMsgTransport(0,1,new JsonMessageParser());
         
         JSONObject json_hello=new JSONObject();
         json_hello.put("IP",host_ipaddr);
         json_hello.put("MAC",host_macaddr);
         json_hello.put("type","Connection setup");
         Message msg_hello=new StringMessage(json_hello.toString());
         msg_hello.setRemoteSocketAddress(remote_soaddr);
         tcp.sendMessage(msg_hello);

         BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
         boolean quit=false;
         while (!quit)
         {  System.out.print("commad> ");
            String command=in.readLine().trim();
            if (command.startsWith("q")) quit=true;
            else
            {  String content_name=command.substring(command.indexOf(" ")+1);
               String type=null;
               if (command.startsWith("s")) type="stored";
               else
               if (command.startsWith("r")) type="refreshed";
               else
               if (command.startsWith("d")) type="deleted";
               else
               if (command.startsWith("h"))
               {  System.out.println("help: commands:\n   s|stored <content-name>\n   r|refreshed <content-name>\n   d|deleted <content-name>\n   h|help\n");
               }
               if (type!=null)
               {  Message msg=new StringMessage("{\"CONTENT NAME\":\""+content_name+"\",\"type\":\""+type+"\"}");
                  msg.setRemoteSocketAddress(remote_soaddr);
                  tcp.sendMessage(msg);
               }
            }
         }
         /*
         Message msg_store=new StringMessage("{\"CONTENT NAME\":\"34371\",\"type\":\"stored\"}");
         msg_store.setRemoteSocketAddress(remote_soaddr);
         tcp.sendMessage(msg_store);
         
         Message msg_refresh=new StringMessage("{\"CONTENT NAME\":\"34371\",\"type\":\"refresh\"}");
         msg_refresh.setRemoteSocketAddress(remote_soaddr);
         tcp.sendMessage(msg_refresh);

         Message msg_delete=new StringMessage("{\"CONTENT NAME\":\"34371\",\"type\":\"delete\"}");
         msg_delete.setRemoteSocketAddress(remote_soaddr);
         tcp.sendMessage(msg_delete);
         */
         tcp.halt();
      }
      catch (Exception e)
      {  e.printStackTrace();
         System.exit(0);
      }
   }
   
}

