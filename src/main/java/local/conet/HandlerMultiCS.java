package local.conet;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.IPv4;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;
import org.zoolu.net.IpPacket;
import org.zoolu.net.message.Message;
import org.zoolu.net.message.MsgTransport;
import org.zoolu.tools.BinAddrTools;
import org.zoolu.tools.BinTools;

/*
 * Handler MULTICS;
 * - TBF AND LEARNING SWITCH BEHAVIOR AND DEFAULT BEHAVIOR
 */
public class HandlerMultiCS extends Handler {
	
	
	/*
	 * Processes PacketIn message Conet-aware.It is only called if tag_based_forwarding
	 * is true. It sets up the static forwarding rules for CONET client / server / cache
	 * server when it sees packets coming from CONET clients and servers After
	 * its processing, it calls the regular processPacketInMessage to perform
	 * regular mac learning
	 * 
	 */
	@Override
	public Command processConetPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		ConetModule cmodule = ConetModule.INSTANCE;
		if(cmodule.debug_multi_cs)
			cmodule.println("CALL HANDLERMULTICS PROCESS CONET PACKETIN");
		try {
			// read in packet data headers by using OFMatch
			OFMatch match = new OFMatch();
			byte[] pkt_data = pi.getPacketData();
			// match.loadFromPacket(pkt_data,pi.getInPort(),sw.getId());
			match.loadFromPacket(pkt_data, pi.getInPort());
			short port_in = match.getInputPort();
			short vlan = match.getDataLayerVirtualLan();
			byte[] eth_src = match.getDataLayerSource();
			byte[] eth_dst = match.getDataLayerDestination();
			//Short eth_vlan = match.getDataLayerVirtualLan();
			short eth_proto = match.getDataLayerType();
			int ip_proto = BinTools.uByte(match.getNetworkProtocol());
			int ip_src = match.getNetworkSource();
			int ip_dst = match.getNetworkDestination();
			int tp_src = U16.f(match.getTransportSource());
			int tp_dst = U16.f(match.getTransportDestination());
			long tag = (((long) tp_src) << 16) + ((long) tp_dst);

//			if (!verbose)
//				verbose = eth_proto == 0x800 && ip_proto == conet_proto;
//			if (verbose && !LEARNING_SWITCH_VERBOSE) {
//				verbose = false;
//				long sw_id = sw.getId();
//				for (int i = 0; !verbose && i < sw_datapath_long.length; i++)
//					if (sw_datapath_long[i] == sw_id)
//						verbose = true;
//			}

			if(cmodule.debug_multi_cs)
					cmodule.println("PACKET-IN: dp=" + sw.getId() + "(" + Long.toHexString(sw.getId()) + "), port=" + port_in
					+ ", vlan=" + vlan + ", mac_src=" + BinTools.asHex(eth_src) + ", mac_dst="
					+ BinTools.asHex(eth_dst) + ", eth_proto=0x" + Integer.toHexString(U16.f(eth_proto))
					+ ", ip_src=" + BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", ip_dst="
					+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_dst)) + ", ip_proto=0x"
					+ Integer.toHexString(ip_proto) + ", tag=0x" + Long.toHexString(tag));

			IpPacket ip_packet = (eth_proto == 0x800) ? IpPacket.parseRawPacket(pkt_data, 14) : null;
			if(cmodule.debug_multi_cs){
				cmodule.println("Pack: "+((ip_packet!=null)? "is" : "is NOT")+" an IP packet");
				if (ip_packet!=null){
					//cmodule.println("IP Packet: "+BinTools.asHex(pkt_data,14,pkt_data.length-14));
					//cmodule.println("IP Options: "+BinTools.asHex(ip_packet.getOptionsBuffer(),ip_packet.getOptionsOffset(),ip_packet.getOptionsLength()));
				}
			}

			// a packet is conet if it has the conet option NB THIS IS NOT USED !!!
//			boolean is_conet = (ip_packet != null && ip_packet.hasOptions()) ? (BinTools.uByte(ip_packet
//					.getOptionsBuffer()[ip_packet.getOptionsOffset()]) == ConetHeader.IP4_OPT_TYPE_CONET) : false;
			//println("DEBUG: "+((is_conet)? "is" :/"is NOT")+" a CONET packet");

			Long dp = Long.valueOf(sw.getId());
			String cache_ipaddr = cmodule.getCacheIpAddress(dp);
			if (cache_ipaddr == null) {
				if (cmodule.debug_multi_cs)
					cmodule.println("No cache server found for this datapath " + Long.toHexString(dp.longValue())+ ". nothing to do.");
			} else { 
				
				// if the datapath is configured with a cache server (the
				// current check is: if the ip address of the cache
				// server is configured for a datapath)
				
				String cache_macaddr = cmodule.getCacheMacAddress(dp);
				// CHECK MAC AND PORT OF CACHE SERVER
				if (ip_src == (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cache_ipaddr))) {
					if(cmodule.debug_multi_cs)
						cmodule.println("CHECK CACHE SERVER: ip_addr=" + cache_ipaddr + ", mac_addr=" + cache_macaddr
								+ ", port=" + port_in);
				}
				
				boolean isclient = cmodule.clientsnet.doyoubelong(IPv4.fromIPv4Address(ip_src));
				boolean isserver = cmodule.serversnet.doyoubelong(IPv4.fromIPv4Address(ip_src));
				
				
				if(cmodule.getConetNodePort(dp, IPv4.fromIPv4Address(ip_src))==0 && (isclient || isserver)){
					if(cmodule.debug_no_conf)
						cmodule.println("It's A New Host");
					cmodule.setConetNodePort(dp, IPv4.fromIPv4Address(ip_src), port_in);
					cmodule.setConetNodeMacAddress(IPv4.fromIPv4Address(ip_src), eth_src);
					if(isclient){
						if(cmodule.debug_no_conf){
							cmodule.println("It's A New Client");
							cmodule.println("Learn Conet Client: eth_proto=" + Integer.toString(eth_proto, 16) + ",ip_src="
									+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", port_in=" + port_in);
						}
						
						// SET ICN-CLIENT FORWARDING RULES
						forwardToClient(dp, OFFlowMod.OFPFC_ADD, vlan, eth_src, ip_src, port_in);
					}
					else if(isserver){
						if(cmodule.debug_no_conf){
							cmodule.println("It's A New Server");
							cmodule.println("Learn Conet Server: eth_proto=" + Integer.toString(eth_proto, 16) + ",ip_src="
									+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", port_in=" + port_in);
						}
						// SET ICN-SERVER FORWARDING RULES
						forwardToServer(dp, OFFlowMod.OFPFC_ADD, vlan, eth_src, ip_src, port_in);
					}
					
				}
				
				
//				// LEARN MAC AND PORT OF ICN CLIENTS
//				for (int i = 0; cmodule.conet_clients != null && i < cmodule.conet_clients.length; i++) {
//					
//					// for each existing client IP address, if the address is
//					// already registered, continue
//					// note that this does not work well if clients move around
//					
//					if (cmodule.getConetNodePort(dp, cmodule.conet_clients[i]) > 0)
//						continue;
//					
//					int conet_ip_addr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cmodule.conet_clients[i]));
//					if (ip_src == conet_ip_addr) { 
//						
//						// we check that the IP src is a conet client address,
//						// we create our own database of forwarding information
//						// for each CONET client
//						// we use this database when we disable
//						// tag_based_forwarding and then we reenable it
//						// to recreate all static forwarding rules without
//						// waiting for new packet in messages
//						
//						if(cmodule.debug_multi_cs)
//						cmodule.println("LEARN CONET CLIENT: eth_proto=" + Integer.toString(eth_proto, 16) + ",ip_src="
//								+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", port_in=" + port_in);
//						
//						cmodule.setConetNodePort(dp, cmodule.conet_clients[i], port_in);
//						cmodule.setConetNodeMacAddress(cmodule.conet_clients[i], eth_src);
//
//						// SET ICN-CLIENT FORWARDING RULES
//						forwardToClient(dp, OFFlowMod.OFPFC_ADD, vlan, eth_src, ip_src, port_in);
//						break;
//					}
//				}

				// LEARN MAC AND PORT OF ICN SERVERS
//				for (int i = 0; cmodule.conet_servers != null && i < cmodule.conet_servers.length; i++) {
//					
//					if (cmodule.getConetNodePort(dp, cmodule.conet_servers[i]) > 0)
//						continue;
//	
//					int conet_ip_addr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cmodule.conet_servers[i]));
//					if (ip_src == conet_ip_addr) {
//						if(cmodule.debug_multi_cs)
//							cmodule.println("LEARN CONET SERVER: eth_proto=" + Integer.toString(eth_proto, 16) + ",ip_src="
//								+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", port_in=" + port_in);
//						cmodule.setConetNodePort(dp, cmodule.conet_servers[i], port_in);
//						cmodule.setConetNodeMacAddress(cmodule.conet_servers[i], eth_src);
//						// SET ICN-SERVER FORWARDING RULES
//						forwardToServer(dp, OFFlowMod.OFPFC_ADD, vlan, eth_src, ip_src, port_in);
//						break;
//					}
//				}
			}

			/*
			 * if (eth_proto==0x806) // ARP PACKETS { println("ARP PACKET: ");
			 * // learn from source mac/vlan
			 * learnFromSourceMac(sw,(short)port_in
			 * ,(Long)Ethernet.toLong(eth_src),(Short)eth_vlan); // forward
			 * packet
			 * doPacketOutForPacketIn(sw,pi,(short)OFPort.OFPP_FLOOD.getValue
			 * ()); return Command.CONTINUE; } else // OTHER PACKETS {
			 * println("IP PACKET:"); String
			 * ip_src=BinAddrTools.bytesToIpv4addr(
			 * BinTools.intTo4Bytes(match.getNetworkSource())); String
			 * ip_dst=BinAddrTools
			 * .bytesToIpv4addr(BinTools.intTo4Bytes(match.getNetworkDestination
			 * ())); int tp_src=U16.f(match.getTransportSource()); int
			 * tp_dst=U16.f(match.getTransportDestination());
			 * 
			 * println("    Switch: "+sw.getId());
			 * println("    Ingress port: "+port_in);
			 * println("    ETH: "+BinTools
			 * .asHex(eth_src)+" -> "+BinTools.asHex(
			 * eth_dst)+" [proto=0x"+Integer.toHexString(eth_proto)+"]");
			 * println
			 * ("    IP: "+ip_src+":"+tp_src+" -> "+ip_dst+":"+tp_dst+" [proto="
			 * +ip_proto+"]"); return processPacketInMessage(sw,pi,cntx); }
			 */

		} catch (Exception e) {
			cmodule.printException(e);
		}
		Command command = super.processConetPacketInMessage(sw, pi, cntx);
		return command;
	}
	
	@Override
	public void processCacheServerMessage(MsgTransport transport, Message msg) {
		super.processCacheServerMessage(transport, msg);
		ConetModule cmodule = ConetModule.INSTANCE;
		JSONObject json_message = (JSONObject) JSONValue.parse(msg.toString());
		
		if(cmodule.debug_multi_cs)
			cmodule.println("Handler MultiCS CALL ProcessCacheServerMessage Json message: " + json_message.toString());
		
		String type = json_message.get("type").toString();
		
		if (type.equalsIgnoreCase("stored") || type.equalsIgnoreCase("refreshed") || type.equalsIgnoreCase("deleted")) {
			
			//Received Different Message - For An Items
			
			if(cmodule.debug_multi_cs)
				cmodule.println("Received :" + type);
			String content_tag = json_message.get("CONTENT NAME").toString();
			long tag = Long.parseLong(content_tag);
			String nid = null;
			if (json_message.containsKey("nid"))
				nid = json_message.get("nid").toString();
			long csn = -1;
			if (json_message.containsKey("csn"))
				csn = Long.parseLong(json_message.get("csn").toString());
			CachedContent content = new CachedContent(nid, csn, tag);
			long datapath = cmodule.DEFAULT_DATAPATH;
			
			String json_cache_mac_addr = BinAddrTools.trimHexString(json_message.get("MAC").toString());
			datapath = cmodule.getDataPathStringFromCacheMacAddress(json_cache_mac_addr);
			if(datapath == -1){
				cmodule.println("HANDLERMULTICSF - ERRORE CACHESERVER DATA PROCESS CACHESERVERMESSAGE");
				msg.getMsgTransportConnection().halt();
				return;
			}
			String dataPathStr = ConetUtility.dpLong2String(datapath);
			if (json_message.containsKey("DataPath"))
				datapath = Long.parseLong(json_message.get("DataPath").toString());
			int dest_ipaddr = 0;
			if (json_message.containsKey("DestIpAddr"))
				dest_ipaddr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(json_message.get("DestIpAddr").toString()));
			
			short command = -1;
			try{
				if(cmodule.debug_multi_cs)
					cmodule.println("ProcessCacheServerMessage Prendo Lock");
				cmodule.lock_contents.lock();
				Hashtable <String , CachedContent> myHT = cmodule.cached_contents.get(dataPathStr);
				if (myHT != null ) {
					if (type.equalsIgnoreCase("stored")) {
						myHT.put(content_tag, content);
						command = OFFlowMod.OFPFC_ADD;
					} else if (type.equalsIgnoreCase("refreshed")) {
						if (!myHT.containsKey(content_tag))
							myHT.put(content_tag, content);
						command = OFFlowMod.OFPFC_ADD;
					} else if (type.equalsIgnoreCase("deleted")) {
						myHT.remove(content_tag);
						command = OFFlowMod.OFPFC_DELETE;
					}
					
					//the logic in the first demo is:
					//if I receive a stored or refreshed message for a given tag, I redirect 
					//to cache all interests towards that tag (whatever the IP destination address of the server)
					//this means adding a row for each server IP address we know
					// set flowtable entry for redirecting packets with the given
					// tag and with the given destination
					
					//XXX WARNING REDIRECT CACHE CACHE DATA CAN BE NULL
					if (dest_ipaddr != 0)
						redirectToCache(datapath, command, (int) dest_ipaddr,(int) -1,(byte) cmodule.conet_proto, tag);
					else{
						
						// set flowtable entry for redirecting packets with the given
						// tag and with any conet server address as destination
						// NOTE: this should be changed when the cache server will send
						// also the destination (together with tag info) within
						// cache-to-controller messages
						super.redirectToCache(datapath, command,(int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cmodule.serversnet.getInfo().getNetworkAddress())),
								(int) cmodule.serversnet.getInfo().getBitmask(), (byte) cmodule.conet_proto, tag);
					}
				}
				cmodule.lock_contents.unlock();
				if(cmodule.debug_multi_cs)
					cmodule.println("ProcessCacheServerMessage Rilascio Lock");
			}
			finally{
				if(cmodule.lock_contents.isHeldByCurrentThread()){
					cmodule.lock_contents.unlock();
					if(cmodule.debug_multi_cs)
						cmodule.println("ProcessCacheServerMessage Finally Rilascio Lock");
				}
			}
		}

	}
	
	
	/*
	 * Adds/deletes forwarding to icn-client and cache-server.
	 */
	protected void forwardToClient(long dp, short command, short vlan, byte[] client_macaddr, int client_ipaddr, short port) {
		// SEND TO ICN-CLIENT AND EVENTUALLY TO CACHE-SERVER
		ConetModule ctemp = ConetModule.INSTANCE;
		if(ctemp.debug_multi_cs)
			ctemp.println("Handler MultiCS FORWARD TO CLIENT");																											// 
		if (!ctemp.debug_disable_redirection) {
			// SEND ONLY TO ICN-CLIENT IF COMING FROM CACHE-SERVER, OTHERWISE SEND TO BOTH ICN-CLIENT AND TO CACHE-SERVER
			if(ctemp.debug_multi_cs)
				ctemp.println("SEND TO BOTH ICN-CLIENT AND CACHE SERVER");
			// SEND ONLY TO ICN-CLIENT IF COMING FROM CACHE-SERVER
			// @@@@@@
			// //doFlowModStatic(switches.get(dp),OFFlowMod.OFPFC_ADD,(short)(PRIORITY_STATIC+1),(short)0,vlan,eth_proto,BinTools.hexStringToBytes(getCacheMacAddress(dp)),0,eth_src,ip_src,(byte)conet_proto,(short)0,(short)0,port_in);
			// doFlowModStatic(switches.get(dp),command,(short)(PRIORITY_STATIC+1),(short)0,vlan,(short)0x800,BinTools.hexStringToBytes(getCacheMacAddress(dp)),0,client_macaddr,client_ipaddr,(byte)conet_proto,(short)0,(short)0,port);
			// Modified by Luca Veltri
			// Date: 15/1/2013
			// Reason: the static rule was deleted when other dynamic rules
			// (e.g. for ARP packet in opposite direction) exPired
			// Changes:
			// doFlowModStatic(switches.get(dp),command,(short)(PRIORITY_STATIC+1),(short)0,vlan,(short)0x800,BinTools.hexStringToBytes(getCacheMacAddress(dp)),0,client_macaddr,client_ipaddr,(byte)conet_proto,(short)0,(short)0,port);
			// Old
			
//			doFlowModStatic(seen_switches.get(dp), command, (short) (PRIORITY_STATIC), (short) 0, vlan, (short) 0x800,
//					null, (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(getCacheIpAddress(dp))), null,
//					client_ipaddr, (byte) conet_proto, (short) 0, (short) 0, port);
			
			String ip_cache = ctemp.getCacheIpAddress(dp);
			String mac_cache = ctemp.getCacheMacAddress(dp);
			short port_cache = ctemp.getCachePort(dp);
			String mac_sw = ctemp.getSwVirtualMacAddr(dp);
			if(ip_cache == null || mac_cache == null || port_cache == -1 || mac_sw == null){
				ctemp.println("HANDLERMULTICS - ERRORE CACHESERVER DATA FORWARDTOCLIENT");
				return;
			}
			
			ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC), (short) 0, vlan, (short) 0x800,
					null, (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(ctemp.cserversnet.getInfo().getNetworkAddress())),
					(int) ctemp.cserversnet.getInfo().getBitmask(), null, client_ipaddr,-1, (byte) ctemp.conet_proto, (short) 0, (short) 0, port, (short) 0);
			
			// Other attempt, that didn't work:
			// doFlowModStatic(switches.get(dp),command,(short)(PRIORITY_STATIC+1),getCachePort(dp),vlan,(short)0x800,BinTools.hexStringToBytes(getCacheMacAddress(dp)),
			// (int)BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(getCacheIpAddress(dp))),client_macaddr,client_ipaddr,(byte)conet_proto,(short)0,(short)0,port);
			// doFlowModStatic(switches.get(dp),command,(short)(PRIORITY_STATIC+1),port,vlan,(short)0x800,client_macaddr,client_ipaddr,BinTools.hexStringToBytes(getCacheMacAddress(dp)),
			// (int)BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(getCacheIpAddress(dp))),(byte)conet_proto,(short)0,(short)0,getCachePort(dp));

			
			// SEND TO ICN-CLIENT AND TO CACHE-SERVER
			Vector<OFAction> actions_vector = new Vector<OFAction>();
			int actions_len = 0;

			OFActionOutput action_output = new OFActionOutput(port, (short) 0xffff);
			actions_vector.addElement(action_output);
			actions_len += OFActionOutput.MINIMUM_LENGTH;

			if (ctemp.change_destination || ctemp.change_mac_destination) {
				OFActionDataLayerDestination action_dl_dest = new OFActionDataLayerDestination();
				action_dl_dest.setDataLayerAddress(BinTools.hexStringToBytes(ctemp.getCacheMacAddress(dp)));
				actions_vector.addElement(action_dl_dest);
				actions_len += OFActionDataLayerDestination.MINIMUM_LENGTH;
			}
			if (ctemp.change_destination) {
				OFActionNetworkLayerDestination action_nw_dest = new OFActionNetworkLayerDestination();
				action_nw_dest.setNetworkAddress((int) BinTools.fourBytesToInt(BinAddrTools
						.ipv4addrToBytes(ctemp.getCacheIpAddress(dp))));
				actions_vector.addElement(action_nw_dest);
				actions_len += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
			}
			if (ctemp.change_mac_source) {
				OFActionDataLayerSource action_dl_src = new OFActionDataLayerSource();
				action_dl_src.setDataLayerAddress(BinTools.hexStringToBytes(ctemp.getSwVirtualMacAddr(dp)));
				actions_vector.addElement(action_dl_src);
				actions_len += OFActionDataLayerSource.MINIMUM_LENGTH;
			}
			action_output = new OFActionOutput(ctemp.getCachePort(dp), (short) 0xffff);
			actions_vector.addElement(action_output);
			actions_len += OFActionOutput.MINIMUM_LENGTH;
			
			// doFlowModStatic(seen_switches.get(dp),OFFlowMod.OFPFC_ADD,PRIORITY_STATIC,(short)0,vlan,eth_proto,null,0,eth_src,(int)ip_src,(byte)conet_proto,(short)0,(short)0,Arrays.asList(actions),((short)actions_len));
			
			ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC), (short) 0, vlan, (short) 0x800, 
					null, (int)IPv4.toIPv4Address(ctemp.serversnet.getInfo().getNetworkAddress()), (int) ctemp.serversnet.getInfo().getBitmask(),
					client_macaddr, client_ipaddr,(int) -1, 
					(byte) ctemp.conet_proto, (short) 0, (short) 0, actions_vector,
					((short) actions_len), (short) 0);
		
			
			
		} else { 
			
			// SEND ONLY TO ICN-CLIENT
			// doFlowModStatic(seen_switches.get(dp),OFFlowMod.OFPFC_ADD,PRIORITY_STATIC,(short)0,vlan,eth_proto,
			// null,0,eth_src,(int)ip_src,(byte)conet_proto,(short)0,(short)0,port_in);
			if(ctemp.debug_multi_cs)
				ctemp.println("SEND ONLY TO ICN-CLIENT");
			
			ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC), (short) 0, vlan, (short) 0x800,
					null, IPv4.toIPv4Address(ctemp.serversnet.getInfo().getNetworkAddress()), (int) ctemp.serversnet.getInfo().getBitmask(),
					client_macaddr, client_ipaddr, (int) -1, (byte) ctemp.conet_proto, (short) 0, (short) 0, port, (short) 0);
		
		}
	}

	/** Adds/deletes forwarding to icn-server. */
	public void forwardToServer(long dp, short command, short vlan, byte[] server_macaddr, int server_ipaddr, short port) { 
		// SEND TO ICN-SERVER
		ConetModule ctemp = ConetModule.INSTANCE;
		if(ctemp.debug_multi_cs)
			ctemp.println("HandlerMultiCS FORWARD TO Server");	
		
		// @@@@@@
		// doFlowModStatic(seen_switches.get(dp),OFFlowMod.OFPFC_ADD,(short)(PRIORITY_STATIC+1),(short)0,vlan,eth_proto,null,0,eth_src,
		// (int)ip_src,(byte)conet_proto,(short)0,(short)0,(short)port_in);

		ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC + 1), (short) 0, vlan, (short) 0x800, 
				null, IPv4.toIPv4Address(ctemp.clientsnet.getInfo().getNetworkAddress()), (int) ctemp.clientsnet.getInfo().getBitmask(), 
				server_macaddr, server_ipaddr, (int) -1,
				(byte) ctemp.conet_proto, (short) 0, (short) 0, port, (short) 0);

	}
	
	@Override
	public void populateAllConetRule(){
		ConetModule cmodule = ConetModule.INSTANCE;
		short command = OFFlowMod.OFPFC_ADD;
		if(cmodule.debug_multi_cs)
			cmodule.println("HandlerMultiCS Populate ALL STATIC ICN STATIC ENTRIES");
		for (Enumeration key = cmodule.seen_cache_server.keys(); key.hasMoreElements();) {
			Long dp = (Long) key.nextElement();
			if(cmodule.debug_no_conf)
				cmodule.println("SW DP: 0x" + ConetUtility.dpLong2String(dp));
			for (Enumeration host = cmodule.ip_mac_mapping.keys(); key.hasMoreElements();){
				String conet_ip = (String) host.nextElement();
				if(cmodule.debug_no_conf)
					cmodule.println("Next Know Conet Host:" + conet_ip);
				byte[] conet_macaddr = cmodule.getConetNodeMacAddress(conet_ip);
				short port = cmodule.getConetNodePort(dp, conet_ip);
				int conet_ipaddr = IPv4.toIPv4Address(conet_ip);
				if(cmodule.clientsnet.doyoubelong(conet_ip)){
					if(cmodule.debug_no_conf){
						cmodule.println("Forward To Client DP: 0x" + ConetUtility.dpLong2String(dp));
						cmodule.println("Forward To Client IP:" + conet_ip);
						cmodule.println("Forward To Client CMD:" + command);
						cmodule.println("Forward To Client port_out:" + port);
					}
					forwardToClient(dp, command, (short) ConetModule.VLAN_ID, conet_macaddr, conet_ipaddr, port);
				}
				else if(cmodule.serversnet.doyoubelong(conet_ip)){
					if(cmodule.debug_no_conf){
						cmodule.println("Forward To Server DP: 0x" + ConetUtility.dpLong2String(dp));
						cmodule.println("Forward To Client IP:" + conet_ip);
						cmodule.println("Forward To Server CMD:" + command);
						cmodule.println("Forward To Server port_out:" + port);
					}
					forwardToServer(dp, command, (short) ConetModule.VLAN_ID, conet_macaddr, conet_ipaddr, port);
				}
			}			
//			for (int i = 0; i < cmodule.conet_clients.length; i++) {
//				int client_ipaddr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cmodule.conet_clients[i]));
//				byte[] client_macaddr = cmodule.getConetNodeMacAddress(cmodule.conet_clients[i]);
//				short port = cmodule.getConetNodePort(dp, cmodule.conet_clients[i]);
//				if(cmodule.debug_multi_cs){
//					cmodule.println("Forward To Client DP: 0x" + ConetUtility.dpLong2String(dp));
//					cmodule.println("Forward To Client IP:" + cmodule.conet_clients[i]);
//					cmodule.println("Forward To Client CMD:" + command);
//					cmodule.println("Forward To Client port_out:" + port);
//				}
//				forwardToClient(dp, command, (short) ConetModule.VLAN_ID, client_macaddr, client_ipaddr, port);
//			}
//			for (int i = 0; i < cmodule.conet_servers.length; i++) {
//				int server_ipaddr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cmodule.conet_servers[i]));
//				byte[] server_macaddr = cmodule.getConetNodeMacAddress(cmodule.conet_servers[i]);
//				short port = cmodule.getConetNodePort(dp, cmodule.conet_servers[i]);
//				if(cmodule.debug_multi_cs){
//					cmodule.println("Forward To Server DP: 0x" + ConetUtility.dpLong2String(dp));
//					cmodule.println("Forward To Client IP:" + cmodule.conet_servers[i]);
//					cmodule.println("Forward To Server CMD:" + command);
//					cmodule.println("Forward To Server port_out:" + port);
//				}
//				forwardToServer(dp, command, (short) ConetModule.VLAN_ID, server_macaddr, server_ipaddr, port);
//			}     
		}
		
	}

}
