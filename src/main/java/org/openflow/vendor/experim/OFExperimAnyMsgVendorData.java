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

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

/**
 * Subclass of OFVendorData representing the vendor data associated with
 * a role request vendor extension.
 * 
 * @author CNIT
 */
public class OFExperimAnyMsgVendorData extends OFExperimMsgVendorData {

    protected static Instantiable<OFVendorData> instantiable =
            new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new OFExperimAnyMsgVendorData();
                }
            };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFRoleRequestVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * The data type value for an OpenMsg
     */
    public static final int EXPERIM_ANY_MSG = 10;

    /**
     * Construct an experimental message vendor data with an unspecified message.
     */
    public OFExperimAnyMsgVendorData() {
        super(EXPERIM_ANY_MSG);
    }
    
    /**
     * Construct a an experimental message vendor data with the specified message.
     * @param message the message 
     */
    public OFExperimAnyMsgVendorData(String message) {
        super(EXPERIM_ANY_MSG, message);
    }
}
