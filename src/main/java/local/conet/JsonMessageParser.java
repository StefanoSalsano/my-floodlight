package local.conet;



import org.zoolu.net.message.*;



/** JsonMessageParser allows the parsing of messages that are delimited by braces.
  * <BR/>
  * If other (pairs of) braces are present within the text, they are skipped.
  */
public class JsonMessageParser extends StringMessageParser
{

   /** Creates a new JsonMessageParser. */
   public JsonMessageParser()
   {  super();
   }


   /** Creates a new JsonMessageParser. */
   /*public JsonMessageParser(String str)
   {  super(str);
   }*/


   /** Creates a new JsonMessageParser. */
   /*public JsonMessageParser(byte[] buf, int off, int len)
   {  super(buf,off,len);
   }*/


   /** From MessageParser. Parses a byte array for a new Message. */
   public Message parseMessage()
   {  while (index<str.length() && str.charAt(index)!='{') index++;
      if (index==str.length()) return null;
      // else
      int count=0;
      int i=index+1;
      while (i<str.length() && (str.charAt(i)!='}' || count>0))
      {  if (str.charAt(i)=='{') count++;
         else
         if (str.charAt(i)=='}') count--;
         i++;
      }
      if (i==str.length()) return null;
      else
      {  i++;
         String msg=str.substring(index,i);
         index=i;
         return new StringMessage(msg);
      }
   }

}