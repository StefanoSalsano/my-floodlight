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

package org.zoolu.tools;



import java.io.*;
import java.net.URL;
import java.util.Vector;



/** Configure manipulates the attributes of a given object,
  * allowing the reading from, setting, and saving them to a file.
  * <BR/>
  * Through Configure it is possible to access of the attributes of a given object
  * to read them from a configuration file or URL, to set new values, and to save to a file.
  */
public class Configure
{
   // **************************** Attributes ***************************

   /** The object that should be configured. */
   Object target;

   /** Object fields. */
   java.lang.reflect.Field[] fields;
   
   /** Whether accessing also protected, private, and package friendly fields. */
   boolean full_access=false;



   // *************************** Costructors ***************************

   /** Creates a new Configure.
     * @param target_obj The object that should be configured */
   public Configure(Object target_obj)
   {  this.target=target_obj;
      //fields=target.getClass().getDeclaredFields();
      fields=target.getClass().getFields();
   }


   /** Creates a new Configure.
     * @param target_obj The object that should be configured
     * @param full_access Whether accessing also protected, private, and package friendly fields */
   public Configure(Object target_obj, boolean full_access)
   {  this.target=target_obj;
      this.full_access=full_access;
      fields=(full_access)? target.getClass().getDeclaredFields() : target.getClass().getFields();
   }


   // ***************************** Methods *****************************

   /** Loads configuration from the given file. */
   public void load(String file)
   {  if (file==null)
      {  return;
      }
      //else
      try
      {  readFrom(new FileReader(file));
      }
      catch (Exception e)
      {  System.err.println("WARNING: error reading file \""+file+"\"");
         return;
      }
   }


   /** Loads configuration from the given URL. */
   public void load(URL url)
   {  if (url==null)
      {  return;
      }
      //else
      try
      {  readFrom(new InputStreamReader(url.openStream()));
      }
      catch (Exception e)
      {  System.err.println("WARNING: error reading from \""+url+"\"");
         return;
      }
   }


   /** Saves configuration on the given file. */
   public void save(String file)
   {  if (file==null) return;
      //else
      try
      {  writeTo(new FileWriter(file));
      }
      catch (IOException e)
      {  System.err.println("ERROR writing on file \""+file+"\"");
      }         
   }


   /** Reads configuration from the given Reader. */
   private void readFrom(Reader rd) throws java.io.IOException
   {  BufferedReader in=new BufferedReader(rd);           
      while (true)
      {  String line=null;
         try { line=in.readLine(); } catch (Exception e) { e.printStackTrace(); System.exit(0); }
         if (line==null) break;
      
         if (!line.startsWith("#"))
         {  parseLine(line);
         }
      } 
      in.close();
   }


   /** Writes Cconfiguration to the given Writer. */
   private void writeTo(Writer wr) throws java.io.IOException
   {  BufferedWriter out=new BufferedWriter(wr);
      out.write(toString());
      out.close();
   }


   /** Parses a text line. */
   protected void parseLine(String line)
   {  String attribute;
      String value;
      int index=line.indexOf("=");
      if (index>0)
      {  attribute=line.substring(0,index).trim();
         value=line.substring(index+1).trim();
      }
      else
      {  attribute=line;
         value="";
      }
      parseAttribute(attribute,value);
   }
   

   /** Gets a string with all parameters (and values). */
   public String toString()
   {  String[] attributes=getAttributes();
      StringBuffer sb=new StringBuffer();
      for (int i=0; i<attributes.length; i++)
      {  String a=(String)attributes[i];
         sb.append(a).append('=').append(getAttributeValue(a)).append('\n');
      }
      return sb.toString();
   }


   /** Gets attriubutes. */
   public String[] getAttributes()
   {  //java.lang.reflect.Field[] fields=target.getClass().getFields();
      String[] attributes=new String[fields.length];
      for (int i=0; i<fields.length; i++)
      {  attributes[i]=fields[i].getName();
      }
      return attributes;
   }
   

   /** Gets attriubute type. */
   public String getAttributeType(String attribute)
   {  String type=null;
      //java.lang.reflect.Field[] fields=target.getClass().getFields();
      for (int i=0; i<fields.length; i++)
      {  String field_name=fields[i].getName();
         if (attribute.equals(field_name))
         {  Class field_class=fields[i].getType();
            if (field_class.isArray())
            {  type=mangleArrayClassType(field_class.getName())+"[]";
            }
            else type=field_class.getName();
            break;
         }
      }
      return type;
   }


   /** Gets attriubute value. */
   public String getAttributeValue(String attribute)
   {  String value=null;
      //java.lang.reflect.Field[] fields=target.getClass().getFields();
      for (int i=0; i<fields.length; i++)
      {  String field_name=fields[i].getName();
         if (attribute.equals(field_name))
         {  Class field_class=fields[i].getType();
            String field_class_name=field_class.getName();
            // @@@@@ force field accessibility
            boolean is_accessible=fields[i].isAccessible();
            if (full_access && !is_accessible) fields[i].setAccessible(true);
            try
            {  if (field_class.isArray())
               {  Object value_obj=fields[i].get(target);
                  int len=(value_obj!=null)? java.lang.reflect.Array.getLength(value_obj) : 0;
                  if (len>0)
                  {  StringBuffer sb=new StringBuffer();
                     sb.append(java.lang.reflect.Array.get(value_obj,0));
                     for (int j=1; j<len; j++) sb.append(',').append(java.lang.reflect.Array.get(value_obj,j));
                     value=sb.toString();
                  }
               }
               else
               {  if (field_class_name.equals("boolean")) value=(((Boolean)fields[i].get(target)).booleanValue())? "yes":"no";
                  else
                  {  Object value_obj=fields[i].get(target);
                     if (value_obj!=null) value=value_obj.toString();
                  }
               }
            }
            catch (Exception e)
            {  e.printStackTrace();
            }
            // @@@@@ restore field accessibility
            if (full_access && !is_accessible) fields[i].setAccessible(false);
            break; 
         }
      }
      return value;
   }


   /** Parses an attribute value. */
   protected void parseAttribute(String attribute, String value)
   {  final char[] DELIM={' ',','};
      Parser par=new Parser(value);
      //java.lang.reflect.Field[] fields=target.getClass().getFields();
      for (int i=0; i<fields.length; i++)
      {  String field_name=fields[i].getName();
         if (attribute.equals(field_name))
         {  Class field_class=fields[i].getType();
            String field_class_name=field_class.getName();
            // @@@@@ force field accessibility
            boolean is_accessible=fields[i].isAccessible();
            if (full_access && !is_accessible) fields[i].setAccessible(true);
            try
            {  if (field_class.isArray())
               {  field_class_name=mangleArrayClassType(field_class_name);
                  Object value_obj=fields[i].get(target);
                  int len=(value_obj!=null)? java.lang.reflect.Array.getLength(value_obj) : 0;
                  if (field_class_name.equals("java.lang.String"))
                  {  String[] str_array=par.getWordArray(DELIM);
                     if (len>0)
                     {  String[] aux=new String[len+str_array.length];
                        for (int j=0; j<aux.length; j++) aux[j]=(j<len)? (String)java.lang.reflect.Array.get(value_obj,j) : str_array[j-len];
                        str_array=aux;
                     }
                     fields[i].set(target,str_array);
                  }
                  else
                  if (field_class_name.equals("int"))
                  {  int[] int_array=par.getIntArray(DELIM);
                     if (len>0)
                     {  int[] aux=new int[len+int_array.length];
                        for (int j=0; j<aux.length; j++) aux[j]=(j<len)? ((Integer)java.lang.reflect.Array.get(value_obj,j)).intValue() : int_array[j-len];
                        int_array=aux;
                     }
                     fields[i].set(target,int_array);
                  }
                  else
                  if (field_class_name.equals("long"))
                  {  long[] long_array=par.getLongArray(DELIM);
                     if (len>0)
                     {  long[] aux=new long[len+long_array.length];
                        for (int j=0; j<aux.length; j++) aux[j]=(j<len)? ((Long)java.lang.reflect.Array.get(value_obj,j)).longValue() : long_array[j-len];
                        long_array=aux;
                     }
                     fields[i].set(target,long_array);
                  }
               }
               else
               {  if (field_class_name.equals("java.lang.String")) fields[i].set(target,par.getRemainingString().trim());
                  else
                  if (field_class_name.equals("byte")) fields[i].set(target,new Byte((byte)par.getInt()));
                  else
                  if (field_class_name.equals("char")) fields[i].set(target,new Character(par.getChar()));
                  else
                  if (field_class_name.equals("double")) fields[i].set(target,new Double(par.getDouble()));
                  else
                  if (field_class_name.equals("float")) fields[i].set(target,new Float((float)par.getDouble()));
                  else
                  if (field_class_name.equals("int")) fields[i].set(target,new Integer(par.getInt()));
                  else
                  if (field_class_name.equals("long")) fields[i].set(target,new Long(par.getInt()));
                  else
                  if (field_class_name.equals("short")) fields[i].set(target,new Short((short)par.getInt()));
                  else
                  if (field_class_name.equals("boolean")) fields[i].set(target,new Boolean(par.getString().toLowerCase().startsWith("y")));
               }
            }
            catch (Exception e)
            {  e.printStackTrace();
            }
            // @@@@@ restore field accessibility
            if (full_access && !is_accessible) fields[i].setAccessible(false);
            break;
         } 
      }
   }


   /** Mangles array class name. */
   private static String mangleArrayClassType(String array_class_name)
   {  if (array_class_name.charAt(1)=='L') return array_class_name.substring(2,array_class_name.length()-1);
      else return manglePrimitiveType(array_class_name.charAt(1));
   }


   /** Mangles primitive type. */
   private static String manglePrimitiveType(char type)
   {  switch (type)
      {  case 'B': return "byte";
         case 'C': return "char";
         case 'D': return "double";
         case 'F': return "float";
         case 'I': return "int";
         case 'J': return "long";
         case 'S': return "short";
         case 'Z': return "boolean";
         case 'V': return "void";
      }
      return null;
   }

}
