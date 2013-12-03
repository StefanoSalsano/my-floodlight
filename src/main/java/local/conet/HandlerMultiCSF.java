package local.conet;

import java.util.Vector;

import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionOutput;
import org.zoolu.tools.BinAddrTools;
import org.zoolu.tools.BinTools;

public class HandlerMultiCSF extends HandlerMultiCS {
	
	@Override
	protected void forwardToClient(long dp, short command, short vlan, byte[] client_macaddr, int client_ipaddr, short port) {
		ConetModule ctemp = ConetModule.INSTANCE;
		if(ctemp.debug_multi_csf)
			ctemp.println("HandlerMultiCSF FORWARD TO CLIENT");	
		if (!ctemp.debug_disable_redirection) {
			// SEND ONLY TO ICN-CLIENT IF COMING FROM CACHE-SERVER,
			// SEND TO BOTH ICN-CLIENT AND TO CACHE-SERVER IF COMING FROM FOREIGN SERVER
			// SEND TO ICN_CLIENT IF COMING FROM HOME SERVER
			if(ctemp.debug_multi_cs)
				ctemp.println("HandlerMultiCSF SEND TO BOTH ICN-CLIENT AND CACHE SERVER");
			
			Pair<String,Integer> hserver = ctemp.getHomeServerRange(dp);
			String ip_cache = ctemp.getCacheIpAddress(dp);
			String mac_cache = ctemp.getCacheMacAddress(dp);
			short port_cache = ctemp.getCachePort(dp);
			String mac_sw = ctemp.getSwVirtualMacAddr(dp);
			if(ip_cache == null || mac_cache == null || hserver==null || port_cache == -1 || mac_sw == null){
				ctemp.println("HANDLERMULTICSF - ERRORE CACHESERVER DATA FORWARDTOCLIENT");
				return;
			}
			
			// SEND ONLY TO ICN-CLIENT IF COMING FROM CACHE-SERVER
			ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC), (short) 0, vlan, (short) 0x800,
					null, (int) BinTools.fourBytesToInt(BinAddrTools.ipv4addrToBytes(ctemp.cservers)),(int) 24, null,
					client_ipaddr,-1, (byte) ctemp.conet_proto, (short) 0, (short) 0, port, (short) 0);
			
			
			
			// SEND TO ICN-CLIENT IF COMING FROM HOME SERVER
			ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC+50), (short) 0, vlan, (short) 0x800,
					null, IPv4.toIPv4Address(hserver.first), hserver.second, client_macaddr, client_ipaddr, -1, (byte) ctemp.conet_proto,
					(short) 0, (short) 0, port, (short) 0);
			
			
			// SEND TO BOTH ICN-CLIENT AND TO CACHE-SERVER IF COMING FROM FOREIGN SERVER
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
						
			ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC), (short) 0, vlan, (short) 0x800, 
					null, (int)IPv4.toIPv4Address(ctemp.servers), (int) 24,
					client_macaddr, client_ipaddr,(int) -1, 
					(byte) ctemp.conet_proto, (short) 0, (short) 0, actions_vector,
					((short) actions_len), (short) 0);	
			
		} else { 
			
			// SEND ONLY TO ICN-CLIENT
			if(ctemp.debug_multi_csf)
				ctemp.println("HandlerMultiCS SEND ONLY TO ICN-CLIENT");
			
			ctemp.doFlowModStatic(ctemp.seen_switches.get(dp), command, (short) (ConetModule.PRIORITY_STATIC), (short) 0, vlan, (short) 0x800,
					null, IPv4.toIPv4Address(ctemp.servers), (int) 24,
					client_macaddr, client_ipaddr, (int) -1,
					(byte) ctemp.conet_proto, (short) 0, (short) 0, port, (short) 0);
		
		}
	
	}

}
