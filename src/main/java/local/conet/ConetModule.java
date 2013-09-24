package local.conet;

import java.util.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.protocol.*;
import org.openflow.protocol.action.*;
import org.openflow.util.U16;
import org.openflow.util.LRULinkedHashMap;
import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.module.*;
import net.floodlightcontroller.core.types.MacVlanPair;

import net.floodlightcontroller.packet.Ethernet;

import local.conet.*;
import org.zoolu.net.IpPacket;
import org.zoolu.net.message.*;
import org.zoolu.tools.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import net.floodlightcontroller.restserver.IRestApiService;

public class ConetModule implements IFloodlightModule, IOFMessageListener, MsgTransportListener {

	/** Configuration file */
	protected static final String CONFIG_FILE = "conetcontroller.conf";

	/** Priority for static table entries */
	protected static final short PRIORITY_STATIC = 200;

	/** Priority for static table entries */
	protected static final short PRIORITY_REDIRECTION = 300;

	/** Conetcontroller verbose output */
	public static boolean CONET_VERBOSE = false;

	/** LearningSwitch verbose output */
	public static boolean LEARNING_SWITCH_VERBOSE = false;

	/** The default (first instanced) ConetModule */
	public static ConetModule INSTANCE = null;

	/** The VLAN ID of the flowspace, it must be set in the configuration file */
	public static int VLAN_ID = 0;

	public static boolean demoSendOpenMsg = false;

	/** Learning switch idle timeout [in seconds] */
	// public short sw_idle_timeout=30;
	public short sw_idle_timeout = 60;

	/** Log */
	Log log = null;

	/** Name of the log file */
	public String log_file = "conetcontroller.log";

	/** Maximum size of a single log file (in MB) */
	public int log_size = 1;

	/** Number of different files that are rotated */
	public int log_rotation = 1;

	/** Duration of each log before rotating (specified in days) */
	public int log_time = 1;

	/** The IP protocol type for Conet packet. */
	public int conet_proto = ConetPacket.PROTO;

	/** Server port for cache-to-controller communication */
	public int json_port = 9999;

	/**
	 * Whether changing mac and ip destination addresses when redirecting
	 * packets to cache-server
	 */
	public boolean change_destination = false;

	/**
	 * Whether changing (only) mac destination addresses when redirecting
	 * packets to cache-server
	 */
	public boolean change_mac_destination = false;

	/**
	 * Whether changing (only) mac source addresses when redirecting packets to
	 * cache-server
	 */
	public boolean change_mac_source = false;

	/** FloodlightProvider */
	protected IFloodlightProviderService floodlightProvider;

	/** Default datapath */
	// NOTE: this should be removed when the cache server will send datapath
	// within cache-to-controller messages
	protected long DEFAULT_DATAPATH = -1;

	/**
	 * Conet Clients - the Conet IP addresses of all Conet clients NB if a node
	 * must act both as client and as server, it will have two Conet IP
	 * addresses
	 */
	public String[] conet_clients = new String[0];

	/** Conet Servers */
	public String[] conet_servers = new String[0];

	/** SW datapath */
	// public long[] sw_datapath=new long[0];
	public String[] sw_datapath = new String[0];
	long[] sw_datapath_long = new long[0];

	/** Cache server ip address (for the corresponding datapath) */
	public String[] cache_ip_addr = new String[0];

	/** Cache server mac address (for the corresponding datapath) 
	 * it will be stored in the format: 0203000000b0
	 * (colons can be present in conetcontroller.conf, they will be removed) 
	 */
	public String[] cache_mac_addr = new String[0];

	/** Cache server port of the switch (for the corresponding datapath) */
	// public short[] cache_port=new short[0];
	public int[] cache_port = new int[0];

	/**
	 * Switch virtual mac address used as source address when redirecting
	 * packets to cache server
	 */
	public String[] sw_virtual_mac_addr = new String[0];

	/**
	 * DEBUG: whether disabling the redirection to cache-server of data packets
	 * coming from icn-servers
	 */
	public boolean debug_disable_redirection = false;

	/**
	 * DEBUG: whether disabling any static flow table entries and working only
	 * in transparent learning switch mode
	 */
	public boolean debug_learning_switch_only = false;

	/** Switches that have sent at least a message to the controller
	 *  
	 */
	Hashtable<Long, IOFSwitch> seen_switches = new Hashtable();

	/**
	 * Mapping of Conet IP addresses (String) to SW ports (Short), for each SW
	 * datapath (Long).
	 */
	Hashtable<Long, Hashtable<String, Short>> conet_port_mapping = new Hashtable();

	/** Mapping of Conet IP addresses (String) to mac addresses (byte[]). */
	Hashtable<String, byte[]> ip_mac_mapping = new Hashtable();

	/** Cached contents (HashSet<String>) for each datapath
	 * 
	 * Associates a tag with a Content (nid, csn, tag) 
	 * and records which content are available in a given datapath
	 */
	Hashtable <String, Hashtable<String, CachedContent>> cached_contents = new Hashtable();

	/** Tag-based forwarding */
	public boolean tag_based_forwarding = true;

	/** Sets tag-based forwarding. */
	public void setTBF(boolean tag_based_forwarding) {
		println("TAG-BASED FORWARDING: " + tag_based_forwarding);
		this.tag_based_forwarding = tag_based_forwarding;
		if (!tag_based_forwarding) {
			flushAllContents();
			flushAllIcnNodes(false);
		} else {
			flushAllIcnNodes(true);
		}
	}

	/** Gets tag-based forwarding. */
	public boolean getTBF() {
		return tag_based_forwarding;
	}

	/**
	 * Deletes all flowtable entries for cached contents and clears the DB of
	 * cached contents.
	 */
	public void flushAllContents() {
		if (DEFAULT_DATAPATH >= 0)
			flushAllContents(DEFAULT_DATAPATH);
		else
			println("DEBUG: No default datapath has been set: impossible to delete contents");
	}

	/**
	 * Deletes all flowtable entries for cached contents and clears the DB of
	 * cached contents.
	 */
	public void flushAllContents(long datapath) {
		println("DELETE ALL CONTENTS");
		// reset cached contents
		for (Enumeration i = cached_contents.keys(); i.hasMoreElements();) {
			String content_tag = (String) i.nextElement();
			long tag = Long.parseLong(content_tag);
			// remove flowtable entries that match packets with the given tag
			// and with any icn server as destination
			// NOTE: this should be changed when the cache server will send also
			// the destination (together with tag info) within
			// cache-to-controller messages
			for (int j = 0; j < conet_servers.length; j++)
				redirectToCache(datapath, OFFlowMod.OFPFC_DELETE,
						(int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(conet_servers[j])),
						(byte) conet_proto, tag);
		}
		cached_contents.clear();
	}

	/** Adds/Deletes all ICN-related static entries. */
	public void flushAllIcnNodes(boolean icn_active) {
		short command = (icn_active) ? OFFlowMod.OFPFC_ADD : OFFlowMod.OFPFC_DELETE;
		println(((icn_active) ? "ADD" : "DELETE") + " ALL ICN STATIC ENTRIES");
		for (int k = 0; k < sw_datapath_long.length; k++) {
			Long dp = sw_datapath_long[k];
			for (int i = 0; i < conet_clients.length; i++) {
				int client_ipaddr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(conet_clients[i]));
				byte[] client_macaddr = getConetNodeMacAddress(conet_clients[i]);
				short port = getConetNodePort(dp, conet_clients[i]);
				forwardToClient(dp, command, (short) VLAN_ID, client_macaddr, client_ipaddr, port);
			}
			for (int i = 0; i < conet_servers.length; i++) {
				int server_ipaddr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(conet_servers[i]));
				byte[] server_macaddr = getConetNodeMacAddress(conet_servers[i]);
				short port = getConetNodePort(dp, conet_servers[i]);
				forwardToServer(dp, command, (short) VLAN_ID, server_macaddr, server_ipaddr, port);
			}
		}
	}

	/** Gets cache server ip address for the given datapath. */
	private String getCacheIpAddress(long datapath) {
		for (int i = 0; i < sw_datapath.length; i++)
			if (sw_datapath_long[i] == datapath)
				return cache_ip_addr[i];
		// else
		// println("DEBUG: getCacheIpAddress(): "+datapath+"!="+sw_datapath_long[i]+"("+Long.toHexString(sw_datapath_long[i])+")");
		return null;
	}

	/** Gets cache server mac address for the given datapath. */
	private String getCacheMacAddress(long datapath) {
		for (int i = 0; i < sw_datapath.length; i++)
			if (sw_datapath_long[i] == datapath)
				return cache_mac_addr[i];
		return null;
	}

	/** Gets cache server port for the given datapath. */
	private short getCachePort(long datapath) {
		for (int i = 0; i < sw_datapath.length; i++)
			if (sw_datapath_long[i] == datapath)
				return (short) cache_port[i];
		return 0;
	}

	/** Gets sw_virtual_mac_addr for the given datapath. */
	private String getSwVirtualMacAddr(long datapath) {
		for (int i = 0; i < sw_datapath.length; i++)
			if (sw_datapath_long[i] == datapath)
				return sw_virtual_mac_addr[i];
		return null;
	}

	/** Gets conet node port for the given datapath. */
	private short getConetNodePort(Long datapath, String node_ipaddr) {
		if (conet_port_mapping.containsKey(datapath)) {
			Short port = conet_port_mapping.get(datapath).get(node_ipaddr);
			if (port != null)
				return port.shortValue();
		}
		return 0;
	}

	/** Sets conet node port for the given datapath. */
	private void setConetNodePort(Long datapath, String node_ipaddr, short port) {
		if (!conet_port_mapping.containsKey(datapath))
			conet_port_mapping.put(datapath, new Hashtable());
		conet_port_mapping.get(datapath).put(node_ipaddr, Short.valueOf(port));
	}

	/** Gets conet node mac address. */
	private byte[] getConetNodeMacAddress(String node_ipaddr) {
		return ip_mac_mapping.get(node_ipaddr);
	}


	/** Gets datapath (long) from cache server mac address  
	 * 
	 * @param cacheMacAddress
	 * @return -1 if not found
	 */
	private String getDataPathStringFromCacheMacAddress(String cacheMacAddress) {
		for (int i = 0; i < sw_datapath.length; i++)
			if ( cache_mac_addr[i].equals(cacheMacAddress) )
				return sw_datapath[i];
		return null;
	}

	
	/** Sets conet node mac address. */
	private void setConetNodeMacAddress(String node_ipaddr, byte[] node_macaddr) {
		ip_mac_mapping.put(node_ipaddr, node_macaddr);
	}

	/** Gets total number of cached items. */
	public int getCachedItems() {
		return cached_contents.size();
	}

	/** Gets array of cached contents. */
	public CachedContent[] getCachedContents() {
		return cached_contents.values().toArray(new CachedContent[0]);
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if (LEARNING_SWITCH_VERBOSE)
			println("receive(): " + msg);

		// LEARN DATAPATH-TO-SWITCH MAPPING AND DELETE ALL FLOW TABLE ENTRIES
		try {
			Long dp = Long.valueOf(sw.getId());
			if (!seen_switches.containsKey(dp)) { 
				//if the switch is not in our DB it is inserted and
				//all flows are cleaned
				
				seen_switches.put(dp, sw);
				doFlowModDeleteAll(sw, (short) 0);
				// DEBUG: use also the following alternative method
				testDeleteAllFlowTableEntries();
			}
		} catch (Exception e) {
			println("WARNING: receive(): sw.getId() failed.");
		}
		;

		// testFlowModStatic(sw);

		if (msg.getType() == OFType.PACKET_IN) {
			if (debug_learning_switch_only || !tag_based_forwarding)
				return processPacketInMessage(sw, (OFPacketIn) msg, cntx);
			else
				return processConetPacketInMessage(sw, (OFPacketIn) msg, cntx);
		}
		// else
		if (msg.getType() == OFType.PORT_STATUS)
			return processPortStatusMessage(sw, (OFPortStatus) msg);
		// else
		if (msg.getType() == OFType.FLOW_REMOVED)
			return processFlowRemovedMessage(sw, (OFFlowRemoved) msg);
		// else
		if (msg.getType() == OFType.HELLO)
			return processHelloMessage(sw, (OFHello) msg);
		// else
		if (msg.getType() == OFType.ERROR) {
			if (LEARNING_SWITCH_VERBOSE)
				println("LearningSwitch: receive(): received from switch " + sw + " the error: " + msg);
			return Command.CONTINUE;
		}
		// else
		if (LEARNING_SWITCH_VERBOSE)
			println("LearningSwitch: receive(): received from switch " + sw + " the unexpected msg: " + msg);
		return Command.CONTINUE;

	}

	@Override
	public String getName() {
		return "ConetModule";
	}

	/*
	 * @Override public int getId() { // TODO Auto-generated method stub return
	 * 0; }
	 */

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) { // TODO
																		// Auto-generated
																		// method
																		// stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) { // TODO
																			// Auto-generated
																			// method
																			// stub
		return false;
	}

	/** Return the list of interfaces that this module implements. */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() { // TODO
																					// Auto-generated
																					// method
																					// stub
		return null;
	}

	/**
	 * Instantiate (as needed) and return objects that implement each of the
	 * services exported by this module.
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() { // TODO
																							// Auto-generated
																							// method
																							// stub
		return null;
	}

	/** Get a list of Modules that this module depends on. */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	/**
	 * This is a hook for each module to do its internal initialization, e.g.,
	 * call setService(context.getService("Service")) All module dependencies
	 * are resolved when this is called, but not every module is initialized.
	 */
	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		println();
		println("init(): start");
		println();
		macVlanToSwitchPortMap = new ConcurrentHashMap<IOFSwitch, Map<MacVlanPair, Short>>();
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);

		Configure conf = new Configure(this);
		conf.load(CONFIG_FILE);

		// LOG
		if (log == null) {
			log = new RotatingLog(log_file, 1, (((long) log_size) * 1024) * 1024, log_rotation, RotatingLog.DAY,
					log_time);
			log.setTimestamp(true);
		}

		// in case a colon-formatted mac address has been given
		for (int i = 0; i < cache_mac_addr.length; i++)
			cache_mac_addr[i] = BinAddrTools.trimHexString(cache_mac_addr[i]);

		// in case a colon-formatted sw_datapath address has been given
		for (int i = 0; i < sw_datapath.length; i++)
			sw_datapath[i] = BinAddrTools.trimHexString(sw_datapath[i]);
		// convert (String)sw_datapath to (long)sw_datapath_long
		sw_datapath_long = new long[sw_datapath.length];
		for (int i = 0; i < sw_datapath.length; i++)
			sw_datapath_long[i] = Long.parseLong(sw_datapath[i], 16);
		for (int i = 0; i < sw_datapath.length; i++)
			println("sw_datapath[" + i + "]=" + sw_datapath_long[i] + "(" + Long.toHexString(sw_datapath_long[i]) + ")");

		// in case a colon-formatted sw_virtual_mac_addr has been given
		for (int i = 0; i < sw_datapath.length; i++)
			sw_virtual_mac_addr[i] = BinAddrTools.trimHexString(sw_virtual_mac_addr[i]);
		// in case a colon-formatted sw_virtual_mac_addr has been given
		// sw_virtual_mac_addr=BinAddrTools.trimHexString(sw_virtual_mac_addr);

		// set the default datapath
		// NOTE: this should be removed when the cache server will send datapath
		// within cache-to-controller messages
		DEFAULT_DATAPATH = sw_datapath_long[0];

		// set the default ConetModule
		INSTANCE = this;

		println("init(): configuration from '" + CONFIG_FILE + "' file:\n" + conf.toString());
		try { // start json interface server for cache-to-controller
				// communication
			(new TcpMsgTransport(json_port, 10, new JsonMessageParser())).setListener(this);
		} catch (java.io.IOException e) {
			printException(e);
		}
	}

	/**
	 * This is a hook for each module to do its external initializations, e.g.,
	 * register for callbacks or query for state in other modules It is expected
	 * that this function will not block and that modules that want non-event
	 * driven CPU will spawn their own threads.
	 */
	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFMessageListener(OFType.PORT_MOD, this);
		floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
		floodlightProvider.addOFMessageListener(OFType.HELLO, this);
		floodlightProvider.addOFMessageListener(OFType.FLOW_MOD, this);
		floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);

		println("");
		println("startUp(): add Conet REST API");
		println("");
		IRestApiService restApi = context.getServiceImpl(IRestApiService.class);
		restApi.addRestletRoutable(new ConetCoreWebRoutable());
		restApi.addRestletRoutable(new ConetWebRoutable());
		// restApi.addRestletRoutable(new
		// net.floodlightcontroller.core.web.CoreWebRoutable());

		println("ip addr 1 : "
				+ getCacheIpAddress(Long.parseLong(BinAddrTools.trimHexString("00:10:00:00:00:00:00:05"), 16)));
		println("ip addr 2 : "
				+ getCacheIpAddress(Long.parseLong(BinAddrTools.trimHexString("00:10:00:00:00:00:00:06"), 16)));

	}

	/** From MsgTransportListener. When a new message is received. */
	public void onReceivedMessage(MsgTransport transport, Message msg) {
		try {
			JSONObject json_message = (JSONObject) JSONValue.parse(msg.toString());
			println();
			println("Json message: " + json_message.toString());
			// for (Iterator i=json_message.keySet().iterator(); i.hasNext(); )
			// { Object obj=i.next();
			// println("Json obj: "+obj+"="+json_message.get(obj));
			// }
			String type = json_message.get("type").toString();
			if (type.equalsIgnoreCase("Connection setup")) {
				long datapath=DEFAULT_DATAPATH;  //this sets by default as datapath the first one in the configuration file
				if (json_message.containsKey("DataPath")) {
					datapath=Long.parseLong(json_message.get("DataPath").toString());
				} else {
					println("WARNING: datapath address missing in the Connection setup message");
				}

				if (json_message.containsKey("IP")) {
					String json_cache_ip_addr = json_message.get("IP").toString();
					if (!json_cache_ip_addr.equals(getCacheIpAddress(datapath)))
						println("WARNING: cache IP address mismatch!");
				}
				if (json_message.containsKey("MAC")) {
					String json_cache_mac_addr = BinAddrTools.trimHexString(json_message.get("MAC").toString());
					String dataPathStr = getDataPathStringFromCacheMacAddress(json_cache_mac_addr);
					datapath = Long.parseLong(dataPathStr, 16);
					if (!json_cache_mac_addr.equals(getCacheMacAddress(datapath)))
						println("WARNING: cache MAC address mismatch!");
					if (!cached_contents.containsKey(datapath))
						cached_contents.put(dataPathStr, new Hashtable());
					//cached_contents.get(datapath).put(node_ipaddr, Short.valueOf(port));

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
			} else if (tag_based_forwarding && (type.equalsIgnoreCase("stored") || type.equalsIgnoreCase("refreshed") || type
							.equalsIgnoreCase("deleted"))) {
				String content_tag = json_message.get("CONTENT NAME").toString();
				long tag = Long.parseLong(content_tag);
				String nid = null;
				if (json_message.containsKey("nid"))
					nid = json_message.get("nid").toString();
				long csn = -1;
				if (json_message.containsKey("csn"))
					csn = Long.parseLong(json_message.get("csn").toString());
				CachedContent content = new CachedContent(nid, csn, tag);

				long datapath = DEFAULT_DATAPATH;
				
				String json_cache_mac_addr = BinAddrTools.trimHexString(json_message.get("MAC").toString());
				String dataPathStr = getDataPathStringFromCacheMacAddress(json_cache_mac_addr);
				datapath = Long.parseLong(dataPathStr, 16);
				
				if (json_message.containsKey("DataPath"))
					datapath = Long.parseLong(json_message.get("DataPath").toString());
				int dest_ipaddr = 0;
				if (json_message.containsKey("DestIpAddr"))
					dest_ipaddr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(json_message.get(
							"DestIpAddr").toString()));
				

				short command = -1;
				Hashtable <String , CachedContent> myHT = cached_contents.get(dataPathStr);
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
					if (dest_ipaddr != 0)
						redirectToCache(datapath, command, dest_ipaddr, (byte) conet_proto, tag);
					else
						// set flowtable entry for redirecting packets with the given
						// tag and with any conet server address as destination
						// NOTE: this should be changed when the cache server will send
						// also the destination (together with tag info) within
						// cache-to-controller messages
					{
						for (int i = 0; i < conet_servers.length; i++)
							redirectToCache(datapath, command,
									(int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(conet_servers[i])),
									(byte) conet_proto, tag);
					}
				}
			}

			println();
		} catch (Exception e) {
			println("Received message: " + msg);
			printException(e);
		}
	}

	/** From MsgTransportListener. When the transport service terminates. */
	public void onMsgTransportTerminated(MsgTransport transport, Exception error) {
		println("Transport " + transport + " terminated.");
		if (error != null)
			println("Transport " + transport + " terminated due to: " + error.toString());
	}

	/** Adds/removes tag redirection to cache server. */
	public void redirectToCache(long datapath, short command, int dest_ipaddr, byte ip_proto, long tag) {
		Vector<OFAction> actions_vector = new Vector<OFAction>();
		int actions_len = 0;

		if (change_destination || change_mac_destination) {
			OFActionDataLayerDestination action_dl_dest = new OFActionDataLayerDestination();
			action_dl_dest.setDataLayerAddress(BinTools.hexStringToBytes(getCacheMacAddress(datapath)));
			actions_vector.addElement(action_dl_dest);
			actions_len += OFActionDataLayerDestination.MINIMUM_LENGTH;
		}
		if (change_destination) {
			OFActionNetworkLayerDestination action_nw_dest = new OFActionNetworkLayerDestination();
			action_nw_dest.setNetworkAddress((int) BinTools.fourBytesToInt(BinAddrTools
					.ipv4addrToBytes(getCacheIpAddress(datapath))));
			actions_vector.addElement(action_nw_dest);
			actions_len += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
		}
		if (change_mac_source) {
			OFActionDataLayerSource action_dl_src = new OFActionDataLayerSource();
			action_dl_src.setDataLayerAddress(BinTools.hexStringToBytes(getSwVirtualMacAddr(datapath)));
			actions_vector.addElement(action_dl_src);
			actions_len += OFActionDataLayerSource.MINIMUM_LENGTH;
		}
		// short port_out=(command!=OFFlowMod.OFPFC_DELETE)?
		// getCachePort(datapath) : OFPort.OFPP_NONE.getValue();
		// OFActionOutput action_output=new
		// OFActionOutput(port_out,(short)0xffff);
		OFActionOutput action_output = new OFActionOutput(getCachePort(datapath), (short) 0xffff);
		actions_vector.addElement(action_output);
		actions_len += OFActionOutput.MINIMUM_LENGTH;

		IOFSwitch sw = seen_switches.get(Long.valueOf(datapath));
		if (sw != null) {
			// doFlowModStatic(sw,command,PRIORITY_REDIRECTION,(short)0,(short)VLAN_ID,(short)0x800,null,0,null,dest_ipaddr,ip_proto,tag,Arrays.asList(actions),(short)actions_len);
			doFlowModStatic(sw, command, PRIORITY_REDIRECTION, (short) 0, (short) VLAN_ID, (short) 0x800, null, 0,
					null, dest_ipaddr, ip_proto, tag, actions_vector, (short) actions_len);
			// @@@@@@
			// sw.flush();
		} else
			println("WARNING: redirect(): No switch found for datapath " + datapath + ".");
	}

	/**
	 * Adds/deletes forwarding to icn-client and cache-server.
	 * 
	 * @param client_macaddr
	 *            the client mac address to be matched as mac destination
	 * @param client_ipadd
	 *            the client IP address to be matched ad IP destination
	 * @param port
	 *            outgoing port
	 */
	public void forwardToClient(long dp, short command, short vlan, byte[] client_macaddr, int client_ipaddr, short port) {
		// SEND TO ICN-CLIENT AND EVENTUALLY TO CACHE-SERVER
																															// 
		if (!debug_disable_redirection) {
			// SEND ONLY TO ICN-CLIENT IF COMING FROM CACHE-SERVER, OTHERWISE SEND TO BOTH ICN-CLIENT AND TO CACHE-SERVER

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
			doFlowModStatic(seen_switches.get(dp), command, (short) (PRIORITY_STATIC + 1), (short) 0, vlan, (short) 0x800,
					null, (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(getCacheIpAddress(dp))), null,
					client_ipaddr, (byte) conet_proto, (short) 0, (short) 0, port);
			// Other attempt, that didn't work:
			// doFlowModStatic(switches.get(dp),command,(short)(PRIORITY_STATIC+1),getCachePort(dp),vlan,(short)0x800,BinTools.hexStringToBytes(getCacheMacAddress(dp)),(int)BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(getCacheIpAddress(dp))),client_macaddr,client_ipaddr,(byte)conet_proto,(short)0,(short)0,port);
			// doFlowModStatic(switches.get(dp),command,(short)(PRIORITY_STATIC+1),port,vlan,(short)0x800,client_macaddr,client_ipaddr,BinTools.hexStringToBytes(getCacheMacAddress(dp)),(int)BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(getCacheIpAddress(dp))),(byte)conet_proto,(short)0,(short)0,getCachePort(dp));

			// SEND TO ICN-CLIENT AND TO CACHE-SERVER
			Vector<OFAction> actions_vector = new Vector<OFAction>();
			int actions_len = 0;

			OFActionOutput action_output = new OFActionOutput(port, (short) 0xffff);
			actions_vector.addElement(action_output);
			actions_len += OFActionOutput.MINIMUM_LENGTH;

			if (change_destination || change_mac_destination) {
				OFActionDataLayerDestination action_dl_dest = new OFActionDataLayerDestination();
				action_dl_dest.setDataLayerAddress(BinTools.hexStringToBytes(getCacheMacAddress(dp)));
				actions_vector.addElement(action_dl_dest);
				actions_len += OFActionDataLayerDestination.MINIMUM_LENGTH;
			}
			if (change_destination) {
				OFActionNetworkLayerDestination action_nw_dest = new OFActionNetworkLayerDestination();
				action_nw_dest.setNetworkAddress((int) BinTools.fourBytesToInt(BinAddrTools
						.ipv4addrToBytes(getCacheIpAddress(dp))));
				actions_vector.addElement(action_nw_dest);
				actions_len += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
			}
			if (change_mac_source) {
				OFActionDataLayerSource action_dl_src = new OFActionDataLayerSource();
				action_dl_src.setDataLayerAddress(BinTools.hexStringToBytes(getSwVirtualMacAddr(dp)));
				actions_vector.addElement(action_dl_src);
				actions_len += OFActionDataLayerSource.MINIMUM_LENGTH;
			}
			action_output = new OFActionOutput(getCachePort(dp), (short) 0xffff);
			actions_vector.addElement(action_output);
			actions_len += OFActionOutput.MINIMUM_LENGTH;

			// @@@@@@
			// doFlowModStatic(seen_switches.get(dp),OFFlowMod.OFPFC_ADD,PRIORITY_STATIC,(short)0,vlan,eth_proto,null,0,eth_src,(int)ip_src,(byte)conet_proto,(short)0,(short)0,Arrays.asList(actions),((short)actions_len));
			doFlowModStatic(seen_switches.get(dp), command, PRIORITY_STATIC, (short) 0, vlan, (short) 0x800, null, 0,
					client_macaddr, client_ipaddr, (byte) conet_proto, (short) 0, (short) 0, actions_vector,
					((short) actions_len));
		} else { // SEND ONLY TO ICN-CLIENT
					// doFlowModStatic(seen_switches.get(dp),OFFlowMod.OFPFC_ADD,PRIORITY_STATIC,(short)0,vlan,eth_proto,null,0,eth_src,(int)ip_src,(byte)conet_proto,(short)0,(short)0,port_in);
			doFlowModStatic(seen_switches.get(dp), command, PRIORITY_STATIC, (short) 0, vlan, (short) 0x800, null, 0,
					client_macaddr, client_ipaddr, (byte) conet_proto, (short) 0, (short) 0, port);
		}
	}

	/** Adds/deletes forwarding to icn-server. */
	public void forwardToServer(long dp, short command, short vlan, byte[] server_macaddr, int server_ipaddr, short port) { 
		// SEND TO ICN-SERVER
	    // @@@@@@
		// doFlowModStatic(seen_switches.get(dp),OFFlowMod.OFPFC_ADD,(short)(PRIORITY_STATIC+1),(short)0,vlan,eth_proto,null,0,eth_src,(int)ip_src,(byte)conet_proto,(short)0,(short)0,(short)port_in);
		doFlowModStatic(seen_switches.get(dp), command, (short) (PRIORITY_STATIC + 1), (short) 0, vlan, (short) 0x800, null,
				0, server_macaddr, server_ipaddr, (byte) conet_proto, (short) 0, (short) 0, port);
	}

	// ***************************** Process message
	// *****************************

	/** Processes HELLO message. */
	protected Command processHelloMessage(IOFSwitch sw, OFHello hello) { // Do nothing; it could be removed.
		return Command.CONTINUE;
	}

	/**
	 * Processes PacketIn message Conet-aware. <br>
	 * It is only called if tag_based_forwarding is true<br>
	 * It sets up the static forwarding rules for CONET client / server / cache
	 * server when it sees packets coming from CONET clients and servers After
	 * its processing, it calls the regular processPacketInMessage to perform
	 * regular mac learning
	 * 
	 */
	protected Command processConetPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		boolean verbose = CONET_VERBOSE;

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
			Short eth_vlan = match.getDataLayerVirtualLan();
			short eth_proto = match.getDataLayerType();
			int ip_proto = BinTools.uByte(match.getNetworkProtocol());
			int ip_src = match.getNetworkSource();
			int ip_dst = match.getNetworkDestination();
			int tp_src = U16.f(match.getTransportSource());
			int tp_dst = U16.f(match.getTransportDestination());
			long tag = (((long) tp_src) << 16) + ((long) tp_dst);

			if (!verbose)
				verbose = eth_proto == 0x800 && ip_proto == conet_proto;
			if (verbose && !LEARNING_SWITCH_VERBOSE) {
				verbose = false;
				long sw_id = sw.getId();
				for (int i = 0; !verbose && i < sw_datapath_long.length; i++)
					if (sw_datapath_long[i] == sw_id)
						verbose = true;
			}

			if (verbose)
				println("PACKET-IN: dp=" + sw.getId() + "(" + Long.toHexString(sw.getId()) + "), port=" + port_in
						+ ", vlan=" + vlan + ", mac_src=" + BinTools.asHex(eth_src) + ", mac_dst="
						+ BinTools.asHex(eth_dst) + ", eth_proto=0x" + Integer.toHexString(U16.f(eth_proto))
						+ ", ip_src=" + BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", ip_dst="
						+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_dst)) + ", ip_proto=0x"
						+ Integer.toHexString(ip_proto) + ", tag=0x" + Long.toHexString(tag));

			IpPacket ip_packet = (eth_proto == 0x800) ? IpPacket.parseRawPacket(pkt_data, 14) : null;
			// println("DEBUG: "+((ip_packet!=null)? "is" :
			// "is NOT")+" an IP packet");
			// if (ip_packet!=null)
			// {
			// println("DEBUG: ip packet: "+BinTools.asHex(pkt_data,14,pkt_data.length-14));
			// println("DEBUG: ip options: "+BinTools.asHex(ip_packet.getOptionsBuffer(),ip_packet.getOptionsOffset(),ip_packet.getOptionsLength()));
			// }

			// a packet is conet if it has the conet option NB THIS IS NOT USED
			// !!!
			boolean is_conet = (ip_packet != null && ip_packet.hasOptions()) ? (BinTools.uByte(ip_packet
					.getOptionsBuffer()[ip_packet.getOptionsOffset()]) == ConetHeader.IP4_OPT_TYPE_CONET) : false;
			// println("DEBUG: "+((is_conet)? "is" :
			// "is NOT")+" a CONET packet");

			Long dp = Long.valueOf(sw.getId());
			// LEARN DATAPATH-TO-SWITCH MAPPING AND DELETE ALL FLOW TABLE
			// ENTRIES
			// if (!switches.containsKey(dp))
			// { switches.put(dp,sw);
			// doFlowModDeleteAll(sw,vlan);
			// }

			String cache_ipaddr = getCacheIpAddress(dp);
			if (cache_ipaddr == null) {
				if (CONET_VERBOSE)
					println("DEBUG: no cache server found for this datapath " + Long.toHexString(dp.longValue())
							+ ". nothing to do.");
			} else { // if the datapath is configured with a cache server (the
						// current check is: if the ip address of the cache
						// server is configured for a datapath)
				String cache_macaddr = getCacheMacAddress(dp);
				// CHECK MAC AND PORT OF CACHE SERVER
				if (ip_src == (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(cache_ipaddr))) {
					if (verbose)
						println("CHECK CACHE SERVER: ip_addr=" + cache_ipaddr + ", mac_addr=" + BinTools.asHex(eth_src)
								+ ", port=" + port_in);
				}

				// LEARN MAC AND PORT OF ICN CLIENTS
				for (int i = 0; conet_clients != null && i < conet_clients.length; i++) {
					// for each existing client IP address, if the address is
					// already registered, continue
					// note that this does not work well if clients move around
					if (getConetNodePort(dp, conet_clients[i]) > 0)
						continue;
					// else
					int conet_ip_addr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(conet_clients[i]));
					if (ip_src == conet_ip_addr) { // we check that the IP src is a conet client address,

						// we create our own database of forwarding information
						// for each CONET client
						// we use this database when we disable
						// tag_based_forwarding and then we reenable it
						// to recreate all static forwarding rules without
						// waiting for new packet in messages
						println("LEARN CONET CLIENT: eth_proto=" + Integer.toString(eth_proto, 16) + ",ip_src="
								+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", port_in=" + port_in);
						setConetNodePort(dp, conet_clients[i], port_in);
						setConetNodeMacAddress(conet_clients[i], eth_src);

						// SET ICN-CLIENT FORWARDING RULES
						forwardToClient(dp, OFFlowMod.OFPFC_ADD, vlan, eth_src, ip_src, port_in);
						break;
					}
				}

				// LEARN MAC AND PORT OF ICN SERVERS
				for (int i = 0; conet_servers != null && i < conet_servers.length; i++) {
					if (getConetNodePort(dp, conet_servers[i]) > 0)
						continue;
					// else
					int conet_ip_addr = (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(conet_servers[i]));
					if (ip_src == conet_ip_addr) {
						println("LEARN CONET SERVER: eth_proto=" + Integer.toString(eth_proto, 16) + ",ip_src="
								+ BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(ip_src)) + ", port_in=" + port_in);
						setConetNodePort(dp, conet_servers[i], port_in);
						setConetNodeMacAddress(conet_servers[i], eth_src);
						// SET ICN-SERVER FORWARDING RULES
						forwardToServer(dp, OFFlowMod.OFPFC_ADD, vlan, eth_src, ip_src, port_in);
						break;
					}
				}
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
			printException(e);
		}
		// return processPacketInMessage(sw,pi,cntx);
		Command command = processPacketInMessage(sw, pi, cntx);
		if (verbose)
			println("PACKET-IN: END\n");
		return command;
	}

	// ***************************** Learning switch
	// *****************************

	// flow-mod - for use in the cookie
	public static final int LEARNING_SWITCH_APP_ID = 1;
	// LOOK! This should probably go in some class that encapsulates
	// the app cookie management
	public static final int APP_ID_BITS = 12;
	public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
	public static final long LEARNING_SWITCH_COOKIE = (long) (LEARNING_SWITCH_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

	// for managing our map sizes
	protected static final int MAX_MACS_PER_SWITCH = 1000;

	// normally, setup reverse flow as well. Disable only for using cbench for
	// comparison with NOX etc.
	// protected static final boolean LEARNING_SWITCH_REVERSE_FLOW = true;
	protected static final boolean LEARNING_SWITCH_REVERSE_FLOW = false;

	// more flow-mod defaults
	// protected static final short IDLE_TIMEOUT_DEFAULT = 120;
	protected static final short HARD_TIMEOUT_DEFAULT = 0;
	protected static final short PRIORITY_DEFAULT = 100;

	// Stores the learned state for each switch
	protected Map<IOFSwitch, Map<MacVlanPair, Short>> macVlanToSwitchPortMap;

	/*
	 * protected void addToPortMap(IOFSwitch sw, Long mac, Short vlan, short
	 * portVal) { sw.addToPortMap(mac, vlan, portVal); }
	 * 
	 * 
	 * protected void removeFromPortMap(IOFSwitch sw, Long mac, Short vlan) {
	 * sw.removeFromPortMap(mac, vlan); }
	 * 
	 * 
	 * protected Short getFromPortMap(IOFSwitch sw, Long mac, Short vlan) {
	 * return sw.getFromPortMap(mac, vlan); }
	 * 
	 * 
	 * protected void removedSwitch(IOFSwitch sw) { sw.clearPortMapTable(); }
	 */

	/**
	 * Adds a host to the MAC/VLAN->SwitchPort mapping
	 * 
	 * @param sw
	 *            The switch to add the mapping to
	 * @param mac
	 *            The MAC address of the host to add
	 * @param vlan
	 *            The VLAN that the host is on
	 * @param portVal
	 *            The switchport that the host is on
	 */
	protected void addToPortMap(IOFSwitch sw, long mac, short vlan, short portVal) {
		Map<MacVlanPair, Short> swMap = macVlanToSwitchPortMap.get(sw);

		if (vlan == (short) 0xffff) {
			// OFMatch.loadFromPacket sets VLAN ID to 0xffff if the packet
			// contains no VLAN tag;
			// for our purposes that is equivalent to the default VLAN ID 0
			vlan = 0;
		}

		if (swMap == null) {
			// May be accessed by REST API so we need to make it thread safe
			swMap = Collections.synchronizedMap(new LRULinkedHashMap<MacVlanPair, Short>(MAX_MACS_PER_SWITCH));
			macVlanToSwitchPortMap.put(sw, swMap);
		}
		swMap.put(new MacVlanPair(mac, vlan), portVal);
	}

	/**
	 * Removes a host from the MAC/VLAN->SwitchPort mapping
	 * 
	 * @param sw
	 *            The switch to remove the mapping from
	 * @param mac
	 *            The MAC address of the host to remove
	 * @param vlan
	 *            The VLAN that the host is on
	 */
	protected void removeFromPortMap(IOFSwitch sw, long mac, short vlan) {
		if (vlan == (short) 0xffff) {
			vlan = 0;
		}
		Map<MacVlanPair, Short> swMap = macVlanToSwitchPortMap.get(sw);
		if (swMap != null)
			swMap.remove(new MacVlanPair(mac, vlan));
	}

	/**
	 * Get the port that a MAC/VLAN pair is associated with
	 * 
	 * @param sw
	 *            The switch to get the mapping from
	 * @param mac
	 *            The MAC address to get
	 * @param vlan
	 *            The VLAN number to get
	 * @return The port the host is on
	 */
	public Short getFromPortMap(IOFSwitch sw, long mac, short vlan) {
		if (vlan == (short) 0xffff) {
			vlan = 0;
		}
		Map<MacVlanPair, Short> swMap = macVlanToSwitchPortMap.get(sw);
		if (swMap != null)
			return swMap.get(new MacVlanPair(mac, vlan));

		// if none found
		return null;
	}

	/**
	 * Clears the MAC/VLAN -> SwitchPort map for all switches
	 */
	public void clearLearnedTable() {
		macVlanToSwitchPortMap.clear();
	}

	/**
	 * Clears the MAC/VLAN -> SwitchPort map for a single switch
	 * 
	 * @param sw
	 *            The switch to clear the mapping for
	 */
	public void clearLearnedTable(IOFSwitch sw) {
		Map<MacVlanPair, Short> swMap = macVlanToSwitchPortMap.get(sw);
		if (swMap != null)
			swMap.clear();
	}

	/*
	 * @Override public synchronized Map<IOFSwitch, Map<MacVlanPair,Short>>
	 * getTable() { return macVlanToSwitchPortMap; }
	 */

	/** Learns from source MAC and VLAN. */
	protected void learnFromSourceMac(IOFSwitch sw, short port_in, Long mac_src, Short vlan) {
		if ((mac_src & 0x010000000000L) == 0) { // if source MAC is a unicast
												// address, learn the port for
												// this MAC/VLAN
			addToPortMap(sw, mac_src, vlan, port_in);
		}
	}

	protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		// read in packet data headers by using OFMatch
		OFMatch match = new OFMatch();
		// match.loadFromPacket(pi.getPacketData(), pi.getInPort(), sw.getId());
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
		Long destMac = Ethernet.toLong(match.getDataLayerDestination());
		Short vlan = match.getDataLayerVirtualLan();
		if ((destMac & 0xfffffffffff0L) == 0x0180c2000000L) {
			if (LEARNING_SWITCH_VERBOSE)
				println("LearningSwitch: processPacketInMessage(): ignoring packet addressed to 802.1D/Q reserved addr: switch "
						+ sw + " vlan " + vlan + " dest MAC " + Long.toString(destMac, 16));
			return Command.STOP;
		}

		boolean verbose = CONET_VERBOSE
				|| (match.getDataLayerType() == 0x800 && match.getNetworkProtocol() == conet_proto);
		if (verbose && !LEARNING_SWITCH_VERBOSE) {
			verbose = false;
			long sw_id = sw.getId();
			for (int i = 0; !verbose && i < sw_datapath_long.length; i++)
				if (sw_datapath_long[i] == sw_id)
					verbose = true;
		}

		// learn from source mac/vlan
		learnFromSourceMac(sw, pi.getInPort(), sourceMac, vlan);

		// output flow-mod and/or packet
		Short outPort = getFromPortMap(sw, destMac, vlan);
		if (outPort == null) {
			// If we haven't learned the port for the dest MAC/VLAN, flood it
			// Don't flood broadcast packets if the broadcast is disabled.
			// XXX For LearningSwitch this doesn't do much. The sourceMac is
			// removed
			// from port map whenever a flow expires, so you would still see
			// a lot of floods.
			if (verbose)
				println("DEBUG: packet out to all ports");
			doPacketOutForPacketIn(sw, pi, OFPort.OFPP_FLOOD.getValue());
		} else if (outPort == match.getInputPort()) {
			if (verbose)
				println("DEBUG: port_in == port_out: packet ignored");
			if (verbose && LEARNING_SWITCH_VERBOSE)
				println("LearningSwitch: processPacketInMessage(): ignoring packet that arrived on same port as learned destination:"
						+ " switch "
						+ sw
						+ " vlan "
						+ vlan
						+ " dest MAC "
						+ Long.toString(destMac, 16)
						+ " port "
						+ outPort);
		} else { // Add flow table entry matching source MAC, dest MAC, VLAN and
					// input port
					// that sends to the port we previously learned for the dest
					// MAC/VLAN. Also
					// add a flow table entry with source and destination MACs
					// reversed, and
					// input and output ports reversed. When either entry
					// expires due to idle
					// timeout, remove the other one. This ensures that if a
					// device moves to
					// a different port, a constant stream of packets headed to
					// the device at
					// its former location does not keep the stale entry alive
					// forever.
			if (verbose)
				println("DEBUG: packet out to port " + outPort + " (and flow add)");
			doFlowAddForPacketIn(sw, pi, outPort);
		}
		return Command.CONTINUE;
	}

	protected Command processPortStatusMessage(IOFSwitch sw, OFPortStatus portStatusMessage) {
		// FIXME This is really just an optimization, speeding up removal of
		// flow
		// entries for a disabled port; think about whether it's really needed
		OFPhysicalPort port = portStatusMessage.getDesc();
		if (LEARNING_SWITCH_VERBOSE)
			println("LearningSwitch: processPortStatusMessage(): received port status: "
					+ portStatusMessage.getReason() + " for port " + port.getPortNumber());
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

	protected Command processFlowRemovedMessage(IOFSwitch sw, OFFlowRemoved flowRemovedMessage) {
		if (flowRemovedMessage.getCookie() != LEARNING_SWITCH_COOKIE) {
			return Command.CONTINUE;
		}
		if (LEARNING_SWITCH_VERBOSE)
			println("LearningSwitch: processFlowRemovedMessage(): " + sw + " flow entry removed " + flowRemovedMessage);
		OFMatch match = flowRemovedMessage.getMatch();
		// When a flow entry expires, it means the device with the matching
		// source
		// MAC address and VLAN either stopped sending packets or moved to a
		// different
		// port. If the device moved, we can't know where it went until it sends
		// another packet, allowing us to re-learn its port. Meanwhile we remove
		// it from the macVlanToPortMap to revert to flooding packets to this
		// device.
		removeFromPortMap(sw, Ethernet.toLong(match.getDataLayerSource()), match.getDataLayerVirtualLan());

		// Also, if packets keep coming from another device (e.g. from ping),
		// the
		// corresponding reverse flow entry will never expire on its own and
		// will
		// send the packets to the wrong port (the matching input port of the
		// expired flow entry), so we must delete the reverse entry explicitly.
		doFlowMod(
				sw,
				OFFlowMod.OFPFC_DELETE,
				-1,
				match.clone()
						.setWildcards(
								((Integer) sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
										& ~OFMatch.OFPFW_DL_VLAN & ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST
										& ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK)
						.setDataLayerSource(match.getDataLayerDestination())
						.setDataLayerDestination(match.getDataLayerSource())
						.setNetworkSource(match.getNetworkDestination())
						.setNetworkDestination(match.getNetworkSource())
						.setTransportSource(match.getTransportDestination())
						.setTransportDestination(match.getTransportSource()), match.getInputPort());
		return Command.CONTINUE;
	}

	// ******************************** Packet out
	// *******************************

	/** Sends out a packet (received as PacketIn) through the given output port. */
	protected void doPacketOutForPacketIn(IOFSwitch sw, OFPacketIn pi, short port_out) {
		doPacketOut(sw, pi.getInPort(), pi.getBufferId(), pi.getPacketData(), port_out, null);
	}

	/** Sends out a packet through the given output port. */
	protected void doPacketOut(IOFSwitch sw, short port_in, int buffer_id, byte[] pkt_data, short port_out,
			FloodlightContext cntx) {
		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		po.setInPort(port_in);
		po.setBufferId(buffer_id);
		int po_len = OFPacketOut.MINIMUM_LENGTH;

		// set actions
		// OFActionOutput action=new OFActionOutput((short)port_out,(short)0);
		OFActionOutput action = new OFActionOutput().setPort((short) port_out);
		// po.setActions(Collections.singletonList((OFAction)action));
		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(action);
		po.setActions(actions);
		po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		po_len += po.getActionsLength();

		// set data if it is included in the PacketIn message
		if (buffer_id == OFPacketOut.BUFFER_ID_NONE) {
			po.setPacketData(pkt_data);
			po_len += pkt_data.length;
		}
		po.setLength(U16.t(po_len));

		// send PacketOut message
		try {
			sw.write(po, cntx);
		} catch (IOException e) {
			printException(e);
		}
	}

	// *************************** Flow add/remove/mod
	// ***************************

	/** Sends a FlowMod message for a PacketIn. */
	protected void doFlowAddForPacketIn(IOFSwitch sw, OFPacketIn pi, short[] ports_out) { // read
																							// in
																							// packet
																							// data
																							// headers
																							// by
																							// using
																							// OFMatch
		OFMatch match = new OFMatch();
		// match.loadFromPacket(pi.getPacketData(),pi.getInPort(),sw.getId());
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());

		// set mach
		// FIXME: current HP switches ignore DL_SRC and DL_DST fields, so we
		// have to match on
		// NW_SRC and NW_DST as well
		// match.setWildcards(((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
		match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN & ~OFMatch.OFPFW_DL_SRC
				& ~OFMatch.OFPFW_DL_DST & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_NW_SRC_MASK
				& ~OFMatch.OFPFW_NW_DST_MASK & ~OFMatch.OFPFW_TP_SRC & ~OFMatch.OFPFW_TP_DST);

		// set actions
		OFAction[] actions = new OFAction[ports_out.length];
		for (int i = 0; i < actions.length; i++)
			actions[i] = new OFActionOutput(ports_out[i], (short) 0xffff);
		doFlowMod(sw, OFFlowMod.OFPFC_ADD, pi.getBufferId(), match, Arrays.asList(actions),
				((short) actions.length * OFActionOutput.MINIMUM_LENGTH));
	}

	/** Sends a FlowMod message for a PacketIn. */
	protected void doFlowAddForPacketIn(IOFSwitch sw, OFPacketIn pi, short port_out) { // Read
																						// in
																						// packet
																						// data
																						// headers
																						// by
																						// using
																						// OFMatch
		OFMatch match = new OFMatch();
		// match.loadFromPacket(pi.getPacketData(),pi.getInPort(),sw.getId());
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());

		// FIXME: current HP switches ignore DL_SRC and DL_DST fields, so we
		// have to match on
		// NW_SRC and NW_DST as well
		// match.setWildcards(((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
		match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN & ~OFMatch.OFPFW_DL_SRC
				& ~OFMatch.OFPFW_DL_DST & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_NW_SRC_MASK
				& ~OFMatch.OFPFW_NW_DST_MASK & ~OFMatch.OFPFW_TP_SRC & ~OFMatch.OFPFW_TP_DST);
		doFlowMod(sw, OFFlowMod.OFPFC_ADD, pi.getBufferId(), match, port_out);

		if (LEARNING_SWITCH_REVERSE_FLOW) {
			doFlowMod(
					sw,
					OFFlowMod.OFPFC_ADD,
					-1,
					match.clone().setDataLayerSource(match.getDataLayerDestination())
							.setDataLayerDestination(match.getDataLayerSource())
							.setNetworkSource(match.getNetworkDestination())
							.setNetworkDestination(match.getNetworkSource())
							.setTransportSource(match.getTransportDestination())
							.setTransportDestination(match.getTransportSource()).setInputPort(port_out),
					match.getInputPort());
		}
	}

	protected void doFlowMod(IOFSwitch sw, short command, int bufferId, OFMatch match, short port_out) {
		List<OFAction> actions = Arrays.asList((OFAction) new OFActionOutput(port_out, (short) 0xffff));
		int actions_len = OFActionOutput.MINIMUM_LENGTH;
		doFlowMod(sw, command, bufferId, match, actions, actions_len);
	}

	protected void doFlowMod(IOFSwitch sw, short command, int bufferId, OFMatch match, List<OFAction> actions,
			int actions_len) {
		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		flowMod.setMatch(match);
		flowMod.setCookie(LEARNING_SWITCH_COOKIE);
		flowMod.setCommand(command);
		// flowMod.setIdleTimeout(IDLE_TIMEOUT_DEFAULT);
		flowMod.setIdleTimeout(sw_idle_timeout);
		flowMod.setHardTimeout(HARD_TIMEOUT_DEFAULT);
		flowMod.setPriority(PRIORITY_DEFAULT);
		flowMod.setBufferId(bufferId);
		flowMod.setOutPort((command == OFFlowMod.OFPFC_DELETE) ? match.getInputPort() : OFPort.OFPP_NONE.getValue());
		flowMod.setFlags((command == OFFlowMod.OFPFC_DELETE) ? 0 : (short) (1 << 0)); // OFPFF_SEND_FLOW_REM

		// set actions
		flowMod.setActions(actions);
		flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + actions_len));

		if (LEARNING_SWITCH_VERBOSE)
			println("LearningSwitch: doFlowMod(): " + sw + " "
					+ ((command == OFFlowMod.OFPFC_DELETE) ? "deleting" : "adding") + " flow mod " + flowMod);

		// and write it out
		try {
			sw.write(flowMod, null);
		} catch (IOException e) {
			println("LearningSwitch: doFlowMod(): ERROR: Failed to write " + flowMod + " to switch " + sw + ": " + e);
		}
	}

	// ***************************** Static flow mod
	// *****************************

	/** Modifies (adds or deletes) a static flow entry. */
	protected void doFlowModStatic(IOFSwitch sw, short command, short priority, short port_in, short vlan,
			short eth_proto, byte[] src_macaddr, int src_ipaddr, byte[] dest_macaddr, int dest_ipaddr, byte ip_proto,
			long tag, short port_out) {
		long src_tport = (tag >> 16) & 0xFFFF;
		long dest_tport = tag & 0xFFFF;
		doFlowModStatic(sw, command, priority, port_in, vlan, eth_proto, src_macaddr, src_ipaddr, dest_macaddr,
				dest_ipaddr, ip_proto, (short) src_tport, (short) dest_tport, port_out);
	}

	/** Modifies (adds or deletes) a static flow entry. */
	protected void doFlowModStatic(IOFSwitch sw, short command, short priority, short port_in, short vlan,
			short eth_proto, byte[] src_macaddr, int src_ipaddr, byte[] dest_macaddr, int dest_ipaddr, byte ip_proto,
			short src_tport, short dest_tport, short port_out) { // if
																	// (command==OFFlowMod.OFPFC_DELETE)
																	// port_out=OFPort.OFPP_NONE.getValue();
		List<OFAction> actions = Arrays.asList((OFAction) new OFActionOutput(port_out, (short) 0xffff));
		int actions_len = OFActionOutput.MINIMUM_LENGTH;
		doFlowModStatic(sw, command, priority, port_in, vlan, eth_proto, src_macaddr, src_ipaddr, dest_macaddr,
				dest_ipaddr, ip_proto, src_tport, dest_tport, actions, actions_len);
	}

	/** Modifies (adds or deletes) a static flow entry. */
	protected void doFlowModStatic(IOFSwitch sw, short command, short priority, short port_in, short vlan,
			short eth_proto, byte[] src_macaddr, int src_ipaddr, byte[] dest_macaddr, int dest_ipaddr, byte ip_proto,
			long tag, List<OFAction> actions, int actions_len) {
		long src_tport = (tag >> 16) & 0xFFFF;
		long dest_tport = tag & 0xFFFF;
		doFlowModStatic(sw, command, priority, port_in, vlan, eth_proto, src_macaddr, src_ipaddr, dest_macaddr,
				dest_ipaddr, ip_proto, (short) src_tport, (short) dest_tport, actions, actions_len);
	}

	/** Modifies (adds or deletes) a static flow entry. */
	protected void doFlowModStatic(IOFSwitch sw, short command, short priority, short port_in, short vlan,
			short eth_proto, byte[] src_macaddr, int src_ipaddr, byte[] dest_macaddr, int dest_ipaddr, byte ip_proto,
			short src_tport, short dest_tport, List<OFAction> actions, int actions_len) {
		OFMatch match = new OFMatch();
		// int wildcards=OFMatch.OFPFW_ALL;
		int wildcards = 0xFFFFFFFF;

		if (port_in > 0) {
			wildcards &= ~OFMatch.OFPFW_IN_PORT;
			match.setInputPort(port_in);
		}
		if (vlan > 0) {
			wildcards &= ~OFMatch.OFPFW_DL_VLAN;
			match.setDataLayerVirtualLan(vlan);
		}
		if (src_macaddr != null) {
			wildcards &= ~OFMatch.OFPFW_DL_SRC;
			match.setDataLayerSource(src_macaddr);
		}
		if (dest_macaddr != null) {
			wildcards &= ~OFMatch.OFPFW_DL_DST;
			match.setDataLayerDestination(dest_macaddr);
		}
		if (eth_proto != 0) {
			wildcards &= ~OFMatch.OFPFW_DL_TYPE;
			match.setDataLayerType(eth_proto);
		}
		if (src_ipaddr != 0) {
			wildcards &= ~OFMatch.OFPFW_NW_SRC_MASK;
			match.setNetworkSource(src_ipaddr);
		}
		if (dest_ipaddr != 0) {
			wildcards &= ~OFMatch.OFPFW_NW_DST_MASK;
			match.setNetworkDestination(dest_ipaddr);
		}
		if (src_tport != 0 || dest_tport != 0) {
			wildcards &= ~OFMatch.OFPFW_TP_SRC & ~OFMatch.OFPFW_TP_DST;
			match.setTransportSource(src_tport).setTransportDestination(dest_tport);
		}
		if (ip_proto != 0) {
			wildcards &= ~OFMatch.OFPFW_NW_PROTO;
			match.setNetworkProtocol(ip_proto);
		}
		match.setWildcards(wildcards);

		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		flowMod.setMatch(match);
		flowMod.setBufferId(-1);
		flowMod.setCookie(LEARNING_SWITCH_COOKIE);
		flowMod.setCommand(command);
		flowMod.setIdleTimeout((short) 0);
		flowMod.setHardTimeout((short) 0);
		flowMod.setPriority(priority);
		flowMod.setOutPort(OFPort.OFPP_NONE.getValue());
		// flowMod.setFlags((short)(1<<0));

		// set actions
		if (actions != null) {
			flowMod.setActions(actions);
			flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + actions_len));
		}

		if (sw == null) {
			println("doFlowModStatic(): ERROR: sw == null");
			return;
		}

		println();
		println("doStaticFlowAdd(): " + sw + " " + ((command == OFFlowMod.OFPFC_DELETE) ? "DELETE" : "ADD")
				+ " static flow entry " + flowMod);
		println();

		// and write it out
		try {
			sw.write(flowMod, null);
			sw.flush();
		} catch (IOException e) {
			if (LEARNING_SWITCH_VERBOSE)
				println("LearningSwitch: doFlowModStatic(): ERROR: Failed to write " + flowMod + " to switch " + sw
						+ ": " + e);
		}
	}

	/** Deletes all flowtable entries. */
	public void doFlowModDeleteAll() {
		println("DEBUG: DELETE ALL STATIC FLOW ENTRIES (FOR ALL SWITCHES)");
		for (Enumeration<IOFSwitch> e = seen_switches.elements(); e.hasMoreElements();) {
			IOFSwitch sw = e.nextElement();
			doFlowModDeleteAll(sw, (short) 0);
		}
	}

	/** Delete all flowtable entries. */
	protected void doFlowModDeleteAll(IOFSwitch sw, short vlan) {
		println();
		println("DEBUG: DELETE ALL STATIC FLOW ENTRIES FOR SW 0x" + Long.toHexString(sw.getId()));
		// doFlowModStatic(sw,OFFlowMod.OFPFC_DELETE,(short)0,(short)0,vlan,(short)0,null,(int)0,null,(int)0,(byte)0,(short)0,(short)0,(short)-1);
		doFlowModStatic(sw, OFFlowMod.OFPFC_DELETE, (short) 0, (short) 0, vlan, (short) 0x800, null, (int) 0, null,
				(int) 0, (byte) 0, (short) 0, (short) 0, null, 0);
	}

	// ******************************* Test methods
	// ******************************

	// ONLY FOR TEST:
	/** Inserts some cached contents, for testing. */
	public void testInsertSomeCachedContents() {
		println("DEBUG: INSERTS SOME CONTENTS FOR TESTING");
		cached_contents.get("0010000000000005").put("869728095", new CachedContent("_example_1k3", 0, 869728095));
		cached_contents.get("0010000000000005").put("869728096", new CachedContent("_example_1k3", 1, 869728096));
		cached_contents.get("0010000000000005").put("869728097", new CachedContent("_example_1k3", 2, 869728097));
		cached_contents.get("0010000000000005").put("123456789", new CachedContent("_abc_def_ghi_jkl", 123, 123456789));
	}

	// ONLY FOR TEST:
	/** Deletes all flowtable entries. */
	public void testDeleteAllFlowTableEntries() {
		println("DELETE ALL FLOWTABLE STATIC ENTRIES (METHOD 2)");
		// set match types to attach flowmod
		OFMatch match = new OFMatch();
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		// match.setNetworkProtocol(IPv4.PROTOCOL_UDP);
		// match.setTransportDestination((short)tp_dst);
		// send flow_mod delete
		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		// match.setWildcards(~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO |
		// OFMatch.OFPFW_TP_DST));
		match.setWildcards(~(OFMatch.OFPFW_DL_TYPE));
		flowMod.setOutPort(OFPort.OFPP_NONE);
		flowMod.setMatch(match);
		flowMod.setCommand(OFFlowMod.OFPFC_DELETE);

		Map<Long, IOFSwitch> switches = floodlightProvider.getSwitches();
		for (IOFSwitch ofSwitch : switches.values()) {
			if (ofSwitch == null) {
				println("DEBUG: "+ ofSwitch + " Switch is not connected!");
				continue;
			}
			try {
				println("DEBUG: " + ofSwitch + " DELETE static flow entry " + flowMod);
				ofSwitch.write(flowMod, null);
				ofSwitch.flush();
			} catch (IOException e) {
				println("DEBUG: tried to write flow_mod delete to " + ofSwitch.getId() + " but failed: "
						+ e.getMessage());
			}
		}
	}

	// ******************************* Log methods
	// *******************************

	/** Prints the Exception. */
	public void printException(Exception e) {
		println("Exception: " + ExceptionPrinter.getStackTraceOf(e));
	}

	/** Prints a blank line. */
	private void println() {
		println("");
	}

	/** Prints a log message. */
	private void println(String str) {
		if (log != null)
			log.println(str);
		// else
		System.out.println("***CONET*** " + str);
	}

	/**
	 * Cached content
	 */
	public class CachedContent {
		/** Content identifier */
		String nid = null;

		/** Chunk sequence number */
		long csn;

		/** Tag */
		long tag;

		/** Creates a new CachedContent. 
		 * @param nid
		 * @param csn
		 * @param tag 
		 */
		public CachedContent(String nid, long csn, long tag) {
			this.tag = tag;
			this.nid = nid;
			this.csn = csn;
		}

		/** Gets content identifier. */
		public String getNid() {
			return nid;
		}

		/** Gets chunk sequence number. */
		public long getCsn() {
			return csn;
		}

		/** Gets tag. */
		public long getTag() {
			return tag;
		}

	}

	// ***************************** Testing methods
	// *****************************

	/** Whether static rules have been already set */
	// boolean static_rules_done=false;

	/** Adds static flow entries, just for testing purpose. */
	/*
	 * protected void testFlowModStatic(IOFSwitch sw) { if (static_rules_done)
	 * return; // else
	 * println("DEBUG: setStaticFlowRules() of sw 0x"+Long.toHexString
	 * (sw.getId()));
	 * 
	 * if (sw.getId()==Long.valueOf("10000000000005",16)) {
	 * static_rules_done=true;
	 * println("DEBUG: ADD STATIC FLOW ENTRY FOR SW 0x"+Long
	 * .toHexString(sw.getId())); //short port_in=(short)4; short
	 * port_in=(short)0; short vlan=(short)VLAN_ID; short
	 * eth_proto=(short)0x800; //byte[]
	 * src_macaddr=BinTools.hexStringToBytes(BinAddrTools
	 * .trimHexString("02:03:00:00:00:b2")); byte[] src_macaddr=null; //int
	 * src_ipaddr
	 * =(int)BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes("192.168.1.23"
	 * )); int src_ipaddr=(int)0; byte[]
	 * dest_macaddr=BinTools.hexStringToBytes(BinAddrTools
	 * .trimHexString("02:03:00:00:00:b9")); int
	 * dest_ipaddr=(int)BinTools.fourBytesToInt
	 * (BinAddrTools.ipv4addrToBytes("192.168.1.8")); byte ip_proto=(byte)17;
	 * short src_tport=(short)2048; short dest_tport=(short)2048; short
	 * port_out=(short)12;
	 * 
	 * short commad=OFFlowMod.OFPFC_ADD; short priority=PRIORITY_REDIRECTION;
	 * 
	 * doFlowModStatic(sw,commad,priority,port_in,vlan,eth_proto,src_macaddr,
	 * src_ipaddr
	 * ,dest_macaddr,dest_ipaddr,ip_proto,src_tport,dest_tport,port_out); } }
	 */

}
