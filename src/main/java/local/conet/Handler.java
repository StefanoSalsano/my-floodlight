package local.conet;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionOutput;
import org.zoolu.net.message.Message;
import org.zoolu.net.message.MsgTransport;
import org.zoolu.net.message.StringMessage;
import org.zoolu.tools.BinAddrTools;
import org.zoolu.tools.BinTools;

/*
 * Default Handler;
 * - NOTBF Or LEARNING SWITCH BEHAVIOR
 */
public class Handler {
	
	/*
	 * Learning Switch Processing
	 * Called from processConetPacketInMessage, when it 
	 * is the active handler;
	 */
	protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		ConetModule cmodule = ConetModule.INSTANCE;
		if(cmodule.debug_multi_cs)
			cmodule.println("CALL HANDLER PROCESS PACKET IN");
		
		// read in packet data headers by using OFMatch
		OFMatch match = new OFMatch();
		// match.loadFromPacket(pi.getPacketData(), pi.getInPort(), sw.getId());
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
		Long destMac = Ethernet.toLong(match.getDataLayerDestination());
		Short vlan = match.getDataLayerVirtualLan();
		if ((destMac & 0xfffffffffff0L) == 0x0180c2000000L) {
			if (cmodule.debug_multi_cs)
			cmodule.println("LearningSwitch: processPacketInMessage(): ignoring packet addressed to 802.1D/Q reserved addr: switch "
						+ sw + " vlan " + vlan + " dest MAC " + Long.toString(destMac, 16));
			return Command.STOP;
		}
		
		if(cmodule.debug_multi_cs){
			cmodule.println("Receive Packet From Port: " + pi.getInPort() + " - With VID: " + vlan
					+ " - SMAC: " + Long.toString(sourceMac, 16) + " - DMAC: " + Long.toString(destMac, 16));
		}

//		boolean verbose = CONET_VERBOSE
//				|| (match.getDataLayerType() == 0x800 && match.getNetworkProtocol() == conet_proto);
//		if (verbose && !LEARNING_SWITCH_VERBOSE) {
//			verbose = false;
//			long sw_id = sw.getId();
//			for (int i = 0; !verbose && i < sw_datapath_long.length; i++)
//				if (sw_datapath_long[i] == sw_id)
//					verbose = true;
//		}

		// learn from source mac/vlan
		cmodule.learnFromSourceMac(sw, pi.getInPort(), sourceMac, vlan);

		// output flow-mod and/or packet
		Short outPort = cmodule.getFromPortMap(sw, destMac, vlan);
		if (outPort == null) {
			
			// If we haven't learned the port for the dest MAC/VLAN, flood it
			// Don't flood broadcast packets if the broadcast is disabled.
			// XXX For LearningSwitch this doesn't do much. The sourceMac is
			// removed
			// from port map whenever a flow expires, so you would still see
			// a lot of floods.
			
			if(cmodule.debug_multi_cs)
				cmodule.println("Packet out to all ports - Flood !!!");
			cmodule.doPacketOutForPacketIn(sw, pi, OFPort.OFPP_FLOOD.getValue());
		} else if (outPort == match.getInputPort()) {
			if(cmodule.debug_multi_cs){
				cmodule.println("DEBUG: port_in == port_out: packet ignored");
				cmodule.println("LearningSwitch: processPacketInMessage(): ignoring packet that arrived on same port as learned destination:"
						+ " switch " + sw + " vlan " + vlan + " dest MAC " + Long.toString(destMac, 16) + " port " + outPort);
			}
		} else { 
			
			// Add flow table entry matching source MAC, dest MAC, VLAN and
			// input port that sends to the port we previously learned for the dest
			// MAC/VLAN. Also add a flow table entry with source and destination MACs
			// reversed, and input and output ports reversed. When either entry
			// expires due to idle timeout, remove the other one. This ensures that if a
			// device moves to a different port, a constant stream of packets headed to
			// the device at its former location does not keep the stale entry alive
			// forever.
			
			if(cmodule.debug_multi_cs)
			cmodule.println("DEBUG: packet out to port " + outPort + " (and flow add)");
			cmodule.doFlowAddForPacketIn(sw, pi, outPort);
		}
		return Command.CONTINUE;



	}

	/*
	 * It is a simply joke so we can have an uniform interface for all the handler;
	 */
	public Command processConetPacketInMessage(IOFSwitch sw, OFPacketIn msg, FloodlightContext cntx) {
		ConetModule cmodule = ConetModule.INSTANCE;
		if(cmodule.debug_multi_cs)
			cmodule.println("CALL HANDLER PROCESS CONET PACKET IN");
		return this.processPacketInMessage(sw, msg, cntx);
	}

	/*
	 * Default Behavior for now, it was copied from original ConetModule 
	 */
	public Command processPortStatusMessage(IOFSwitch sw, OFPortStatus portStatusMessage) {
		// FIXME This is really just an optimization, speeding up removal of flow
		// entries for a disabled port; think about whether it's really needed
		ConetModule cmodule = ConetModule.INSTANCE;
		OFPhysicalPort port = portStatusMessage.getDesc();
		if(cmodule.debug_multi_cs){
			cmodule.println("CALL HANDLER PROCESS PORT STATUS MESSAGE");
			cmodule.println("Received port status: "+ portStatusMessage.getReason()	+ " for port " + port.getPortNumber());
		
		}	
		
		// LOOK! should be using the reason enums - but how?
		if (portStatusMessage.getReason() == 1 || // DELETED
				(portStatusMessage.getReason() == 2 && // MODIFIED and is now
														// down
				((port.getConfig() & OFPhysicalPort.OFPortConfig.OFPPC_PORT_DOWN.getValue()) > 1 || (port.getState() & OFPhysicalPort.OFPortState.OFPPS_LINK_DOWN
						.getValue()) > 1))) {
			// then we should reset the switch data structures
			// LOOK! we could be doing something more intelligent like
			// extract out the macs just assigned to a port, but this is ok for
			// now
			// removedSwitch(sw);
		}
		return Command.CONTINUE;
	}

	/*
	 * Default Behavior for now, it was copied from original ConetModule 
	 */
	public Command processFlowRemovedMessage(IOFSwitch sw, OFFlowRemoved flowRemovedMessage) {
		ConetModule cmodule = ConetModule.INSTANCE;
		if (flowRemovedMessage.getCookie() != ConetModule.LEARNING_SWITCH_COOKIE) {
			return Command.CONTINUE;
		}
		
		if(cmodule.debug_multi_cs)
			cmodule.println("LearningSwitch: processFlowRemovedMessage(): " + sw + " flow entry removed " + flowRemovedMessage);
		OFMatch match = flowRemovedMessage.getMatch();
		if(cmodule.debug_multi_cs)
			cmodule.println("OFMATCH: " + match);
		
		// When a flow entry expires, it means the device with the matching
		// source MAC address and VLAN either stopped sending packets or moved 
		// to a different port. If the device moved, we can't know where it went
		// until it sends another packet, allowing us to re-learn its port. Meanwhile
		// we remove it from the macVlanToPortMap to revert to flooding packets to this
		// device.
		cmodule.removeFromPortMap(sw, Ethernet.toLong(match.getDataLayerSource()), match.getDataLayerVirtualLan());

		// Also, if packets keep coming from another device (e.g. from ping),
		// the corresponding reverse flow entry will never expire on its own
		// and will send the packets to the wrong port (the matching input port
		// of the expired flow entry), so we must delete the reverse entry explicitly.
		cmodule.doFlowMod(sw,OFFlowMod.OFPFC_DELETE,-1,
				match.clone().setWildcards(((Integer) sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
				& ~OFMatch.OFPFW_DL_VLAN & ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST
				& ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK).setDataLayerSource(match.getDataLayerDestination())
				.setDataLayerDestination(match.getDataLayerSource()).setNetworkSource(match.getNetworkDestination())
				.setNetworkDestination(match.getNetworkSource()).setTransportSource(match.getTransportDestination())
				.setTransportDestination(match.getTransportSource()), match.getInputPort());
		return Command.CONTINUE;
	}

	/*
	 * Default Behavior for now, it was copied from original ConetModule 
	 */
	public Command processHelloMessage(IOFSwitch sw, OFHello hello) { // Do nothing; it could be removed.
		ConetModule cmodule = ConetModule.INSTANCE;
		if(cmodule.debug_multi_cs)
			cmodule.println("SW:" + sw.getId() + "Says HELLO " + hello);
		return Command.CONTINUE;
	}

	public void processCacheServerMessage(MsgTransport transport, Message msg) {
		ConetModule cmodule = ConetModule.INSTANCE;
		try {
			JSONObject json_message = (JSONObject) JSONValue.parse(msg.toString());
			if(cmodule.debug_multi_cs)
				cmodule.println("Json message: " + json_message.toString());
			
			// for (Iterator i=json_message.keySet().iterator(); i.hasNext(); )
			// { Object obj=i.next();
			// println("Json obj: "+obj+"="+json_message.get(obj));
			// }
			
			String type = json_message.get("type").toString();
			if (type.equalsIgnoreCase("Connection setup")) {
				if(cmodule.debug_multi_cs)
					cmodule.println("Received :" + type);
				//this sets by default as datapath the first one in the configuration file
				long datapath = cmodule.DEFAULT_DATAPATH;  
				
				if (json_message.containsKey("DataPath")) {
					datapath=Long.parseLong(json_message.get("DataPath").toString());
				} else {
					cmodule.println("WARNING: datapath address missing in the Connection setup message");
				}
	
				if (json_message.containsKey("IP")) {
					String json_cache_ip_addr = json_message.get("IP").toString();
					if (!json_cache_ip_addr.equals(cmodule.getCacheIpAddress(datapath)))
						cmodule.println("WARNING: cache IP address mismatch!");
				}
				
				if (json_message.containsKey("MAC")) {
					String json_cache_mac_addr = BinAddrTools.trimHexString(json_message.get("MAC").toString());
					String dataPathStr = cmodule.getDataPathStringFromCacheMacAddress(json_cache_mac_addr);
					datapath = Long.parseLong(dataPathStr, 16);
					if (!json_cache_mac_addr.equals(cmodule.getCacheMacAddress(datapath)))
						cmodule.println("WARNING: cache MAC address mismatch!");
					if (!cmodule.cached_contents.containsKey(dataPathStr))
						cmodule.cached_contents.put(dataPathStr, new Hashtable<String, CachedContent>());
	
					// reset cached contents
					flushAllContents(datapath);
				
				}
				
				/*
				 * for (Enumeration i=cached_contents.keys();
				 * i.hasMoreElement(); ) { String
				 * content_name=(String)i.nextElement(); long
				 * tag=Long.parseLong(content_name); // remove flowtable entries
				 * that match packets with the given tag and with any icn server
				 * as destination // NOTE: this should be changed when the cache
				 * server will send also the destination (together with tag
				 * info) within cache-to-controller messages for (int j=0;
				 * j<conet_servers.length; j++)
				 * redirect(datapath,OFFlowMod.OFPFC_DELETE
				 * ,(int)BinTools.fourBytesToInt
				 * (BinAddrTools.ipv4addrToBytes(conet_servers
				 * [j])),(byte)conet_proto,tag); } cached_contents.clear();
				 */
				
//		        JSONObject json_hello=new JSONObject();
//				//json_hello.put("controller","10.216.33.109");
//		        json_hello.put("controller","10.216.12.88");
//		        json_hello.put("type","Connection setup reply");
//		        Message msg_hello=new StringMessage(json_hello.toString());
//				transport.sendMessage(msg);
				
			} else if (type.equalsIgnoreCase("Hello request") ) {
		        JSONObject json_hello=new JSONObject();
		        //json_hello.put("controller","10.216.33.109");
		        json_hello.put("controller","10.216.12.88");
		        json_hello.put("type","Hello reply");
		        Message msg_hello=new StringMessage(json_hello.toString());
		        cmodule.println("RECEIVED Hello request - SENDING Hello Reply");
				transport.sendMessage(msg);
			}
			
		} catch (Exception e) {
			cmodule.println("Received message: " + msg);
			cmodule.printException(e);
		}
	}
	
	
	/* 
	 * From MsgTransportListener. When the transport service terminates. 
	 */
	public void onMsgTransportTerminated(MsgTransport transport, Exception error) {
		ConetModule cmodule = ConetModule.INSTANCE;
		cmodule.println("Transport " + transport + " terminated.");
		if (error != null)
			cmodule.println("Transport " + transport + " terminated due to: " + error.toString());
	}


	/*
	 * Deletes all flowtable entries for cached contents and clears the DB of
	 * cached contents.
	 */
	public void flushAllContents(long datapath) {
		ConetModule cmodule = ConetModule.INSTANCE;
		cmodule.println("DELETE ALL CONTENTS in local view (hastable)");
		// reset cached contents
		
		Hashtable <String , CachedContent> myHT = cmodule.cached_contents.get(cmodule.dpLong2String(datapath));
		if (myHT != null ) {
			for (Enumeration i = myHT.keys(); i.hasMoreElements();) {
				String content_tag = (String) i.nextElement();
				long tag = Long.parseLong(content_tag);
				
				// remove flowtable entries that match packets with the given tag
				// and with any icn server as destination
				// NOTE: this should be changed when the cache server will send also
				// the destination (together with tag info) within
				// cache-to-controller messages
				
				cmodule.println("TAG TO BE REMOVED : "+ content_tag);
				redirectToCache(datapath, OFFlowMod.OFPFC_DELETE, (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cmodule.servers)),
						(int) cmodule.bit_servers, (byte) cmodule.conet_proto, tag);
			}
			myHT.clear();
//			cmodule.cached_contents.put(cmodule.dpLong2String(datapath), new Hashtable<String, CachedContent>());
//			System.gc();
		}
	}
	
	/*
	 * Try To Delete All Conet Rule
	 */
	public void deleteAllConetRule(){
		ConetModule cmodule = ConetModule.INSTANCE;
		if(cmodule.debug_multi_cs)
			cmodule.println("DELETE ALL CONET RULE");
		Map<Long, IOFSwitch> switches = cmodule.floodlightProvider.getSwitches();
		int i = 0;
		while(i < cmodule.sw_datapath_long.length){
			if(!switches.containsKey(cmodule.sw_datapath_long[i])){
				cmodule.println("Trovato: " + cmodule.sw_datapath[i] + " - DELETE");
				IOFSwitch sw = switches.get(cmodule.sw_datapath_long[i]);
				cmodule.doFlowModStatic(sw, OFFlowMod.OFPFC_DELETE, (short) 0, (short) 0, (short) ConetModule.VLAN_ID, (short) 0x800, 
						null, (int) IPv4.toIPv4Address(cmodule.net), (int) cmodule.bit_net, 
						null, (int) IPv4.toIPv4Address(cmodule.net), (int) cmodule.bit_net,
						(byte) cmodule.conet_proto, (short) 0, (short) 0, null, 0);
				Hashtable <String , CachedContent> myHT = cmodule.cached_contents.get(cmodule.sw_datapath[i]);
				if(myHT != null)
					myHT.clear();
			}
			else{
				cmodule.println("Non Trovato: " + cmodule.sw_datapath[i]);
			}
			i++;
		}
		
	}
	
	/*
	 * Simple PlaceHolder
	 */
	public void populateAllConetRule(){;}
	
	
	/** Adds/removes tag redirection to cache server. */
	protected void redirectToCache(long datapath, short command, int dest_ipaddr, int dest_cidr, byte ip_proto, long tag) {
		ConetModule cmodule = ConetModule.INSTANCE;
		Vector<OFAction> actions_vector = new Vector<OFAction>();
		int actions_len = 0;
		cmodule.println("CALL REDIRECT TO CACHE");
		if (cmodule.change_destination || cmodule.change_mac_destination) {
			cmodule.println("CAMBIO MAC DST: " + cmodule.getCacheMacAddress(datapath));
			OFActionDataLayerDestination action_dl_dest = new OFActionDataLayerDestination();
			action_dl_dest.setDataLayerAddress(BinTools.hexStringToBytes(cmodule.getCacheMacAddress(datapath)));
			actions_vector.addElement(action_dl_dest);
			actions_len += OFActionDataLayerDestination.MINIMUM_LENGTH;
		}
		if (cmodule.change_destination) {
			cmodule.println("CAMBIO NET DST: " + cmodule.getCacheIpAddress(tag));
			OFActionNetworkLayerDestination action_nw_dest = new OFActionNetworkLayerDestination();
			action_nw_dest.setNetworkAddress((int) BinTools.fourBytesToInt(BinAddrTools
					.ipv4addrToBytes(cmodule.getCacheIpAddress(datapath))));
			actions_vector.addElement(action_nw_dest);
			actions_len += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
		}
		if (cmodule.change_mac_source) {
			cmodule.println("CAMBIO MAC SRC: " + cmodule.getSwVirtualMacAddr(datapath));
			OFActionDataLayerSource action_dl_src = new OFActionDataLayerSource();
			action_dl_src.setDataLayerAddress(BinTools.hexStringToBytes(cmodule.getSwVirtualMacAddr(datapath)));
			actions_vector.addElement(action_dl_src);
			actions_len += OFActionDataLayerSource.MINIMUM_LENGTH;
		}
		
		// short port_out=(command!=OFFlowMod.OFPFC_DELETE)?
		// getCachePort(datapath) : OFPort.OFPP_NONE.getValue();
		// OFActionOutput action_output=new
		// OFActionOutput(port_out,(short)0xffff);
		
		OFActionOutput action_output = new OFActionOutput(cmodule.getCachePort(datapath), (short) 0xffff);
		actions_vector.addElement(action_output);
		actions_len += OFActionOutput.MINIMUM_LENGTH;

		IOFSwitch sw = cmodule.seen_switches.get(Long.valueOf(datapath));
		if (sw != null) {
			
			// doFlowModStatic(sw,command,PRIORITY_REDIRECTION,(short)0,(short)VLAN_ID,(short)0x800,null,0,null,dest_ipaddr,ip_proto,tag,Arrays.asList(actions),(short)actions_len);
			
			cmodule.doFlowModStatic(sw, command, (short)(ConetModule.PRIORITY_REDIRECTION+50), (short) 0, (short) ConetModule.VLAN_ID, (short) 0x800,
					null, IPv4.toIPv4Address(cmodule.clients), cmodule.bit_clients, null, dest_ipaddr, dest_cidr, ip_proto, tag, actions_vector, (short) actions_len);
			
			// @@@@@@
			// sw.flush();
		} else
			cmodule.println("WARNING: redirect(): No switch found for datapath : " + datapath + "(" + Long.toHexString(datapath) + ")");
	}



}
