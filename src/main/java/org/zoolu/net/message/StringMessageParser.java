package org.zoolu.net.message;




/** Abstract class for all MessageParser for a StringMessage.
  */
public abstract class StringMessageParser implements MessageParser
{
   /** Buffer string */
   protected String str;
   
   /** Buffer index */
   protected int index;



   /** Creates a new StringMessageParser. */
   public StringMessageParser()
   {  this.str="";
      index=0;
   }


   /** Creates a new StringMessageParser. */
   public StringMessageParser(String str)
   {  this.str=str;
      index=0;
   }


   /** Creates a new StringMessageParser. */
   public StringMessageParser(byte[] buf, int off, int len)
   {  init(buf,off,len);
   }


   /** From MessageParser. Inits the parser with a new byte array, offset, and length. */
   public StringMessageParser init(byte[] buf, int off, int len)
   {  str=new String(buf,off,len);
      index=0;
      return this;
   }


   /** From MessageParser. Appends new bytes. */
   public MessageParser append(byte[] buf, int off, int len)
   {  StringBuffer sb=new StringBuffer(str.substring(index));
      sb.append(new String(buf,off,len));
      str=sb.toString();
      index=0;
      return this;
   }

}