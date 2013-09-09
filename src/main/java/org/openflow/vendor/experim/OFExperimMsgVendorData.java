/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson & Rob Sherwood, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package org.openflow.vendor.experim;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Class that represents the vendor data in the experimenter
 * extension implemented to support a generic message.
 * 
 * @author CNIT 
 */
public class OFExperimMsgVendorData extends OFExperimVendorData {
    

    protected String message;

    /** 
     * Construct an uninitialized OFExperimMsgVendorData
     */
    public OFExperimMsgVendorData() {
        super();
    }
    
    /**
     * Construct an OFExperimMsgVendorData with the specified data type
     * (e.g. Any) and an unspecified message.
     * @param dataType
     */
    public OFExperimMsgVendorData(int dataType) {
        super(dataType);
    }
    
    /**
     * Construct an OFExperimMsgVendorData with the specified data type
     * (e.g. Any) and message  (a string).
     * @param dataType 
     */
    public OFExperimMsgVendorData(int dataType, String message) {
        super(dataType);
        this.message = message;
    }
    /**
     * @return the string value of the message vendor data
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * @param message the  value of the message vendor data
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @return the total length of the role vendor data (including the padding trailer of
     * 0x00 for 8 bytes alignment
     */
    @Override
    public int getLength() {
        return super.getLength() + message.length();
    }
    
    /**
     * Read the role vendor data from the ChannelBuffer
     * @param data the channel buffer from which we're deserializing
     * @param length the length to the end of the enclosing message
     */
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        message = data.readBytes(length).toString();
    }

    /**
     * Write the role vendor data to the ChannelBuffer
     * @param data the channel buffer to which we're serializing
     */
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeBytes(message.getBytes());
    }
}
