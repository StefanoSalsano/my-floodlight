package local.conet;

import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.util.U16;
import org.zoolu.tools.BinAddrTools;
import org.zoolu.tools.BinTools;

public class ConetListenerRPF extends ConetListener {
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if( (msg.getType() == OFType.PACKET_IN) && !rpfCheck(sw, (OFPacketIn)msg))
			return Command.CONTINUE;
		return super.receive(sw, msg, cntx);
	}

	private boolean rpfCheck(IOFSwitch sw, OFPacketIn pi) {
		ConetModule cmodule = ConetModule.INSTANCE;
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
		Long destMac = Ethernet.toLong(match.getDataLayerDestination());
		Short vlan = match.getDataLayerVirtualLan();
		short eth_proto = match.getDataLayerType();
		int IP_PROTO = BinTools.uByte(match.getNetworkProtocol());
		int IP_SRC = match.getNetworkSource();
		int SRC_MASK_LEN = match.getNetworkSourceMaskLen();
		int IP_DST = match.getNetworkDestination();
		int DST_MASK_LEN = match.getNetworkDestinationMaskLen();
				
		if(cmodule.debug_rpf){
			cmodule.println();
			cmodule.println("ARRIVED: dp=" + sw.getId() + "(" + Long.toHexString(sw.getId()) + "),p_in=" + match.getInputPort()
				+  ",src_mac=" + Long.toHexString(sourceMac) + ",dst_mac=" + Long.toHexString(destMac)
				+ ",vlan=" + vlan + ", eth_proto=0x" + Integer.toHexString(U16.f(eth_proto))
				+ ", ip_proto=0x" + Integer.toHexString(IP_PROTO) 
				+ ", ip_src=" + BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(IP_SRC)) + "/" + SRC_MASK_LEN
				+ ", ip_dst=" + BinAddrTools.bytesToIpv4addr(BinTools.intTo4Bytes(IP_DST)) + "/" + DST_MASK_LEN);
		}
		
		
		if (Integer.toHexString(U16.f(eth_proto)).equalsIgnoreCase("86dd") && 
				(Integer.toHexString(IP_PROTO).equals("00") || Integer.toHexString(IP_PROTO).equals("0"))) {
			if (cmodule.debug_rpf)
			cmodule.println("WARNING - RPFCHECK DISCARD IPV6 HOPBYHOP Packet");
			return false;
		}
		
		List<NodePortTuple> path = null;
		long dpid = 0;
		int port = 0;
		IDevice attachmentPoint = null;
		String temp = ConetUtility.fixMac(sourceMac);
		if(cmodule.debug_rpf)
			cmodule.println("Try To Obtain A Route Towards The Source: " + IPv4.fromIPv4Address(IP_SRC) + " - " + ConetUtility.addColonToMac(temp));
		for (IDevice D : cmodule.floodlightDeviceService.getAllDevices()){
			if(cmodule.debug_rpf)
				cmodule.println(D.toString());
            if(D.toString().contains(ConetUtility.addColonToMac(temp))){
            	attachmentPoint = D;
            	break;
            }
	    }
		
		if(attachmentPoint != null){
			if(cmodule.debug_rpf){
				cmodule.println("\n");
				cmodule.println("Founded AttachmentPoint: " + attachmentPoint);
			}
			SwitchPort[] ports = attachmentPoint.getAttachmentPoints();
			Route r = null;
			if(ports.length > 1){
				if(cmodule.debug_rpf){
					cmodule.println("\n");
					cmodule.println("WARNING MORE THAN ONE ATTACHMENTPOINT - Taking The First That Differ From FFFFFFFFFFFFFFFF: " + ports.toString());
				}
				if(ports[0].getSwitchDPID() == Long.parseLong("FFFFFFFFFFFFFFFF", 16)){
					if(cmodule.debug_rpf){
						cmodule.println("\n");
						cmodule.println("Take The Second AttachmentPoint: " + Long.toHexString(ports[1].getSwitchDPID()) + " - " + ports[1].getPort());
					}
					r = cmodule.floodlightRouting.getRoute(sw.getId(), ports[1].getSwitchDPID());
					dpid = ports[1].getSwitchDPID();
					port = ports[1].getPort();
				}
				else
					if(cmodule.debug_rpf){
						cmodule.println("\n");
						cmodule.println("Take The First AttachmentPoint: " + Long.toHexString(ports[0].getSwitchDPID()) + " - " + ports[0].getPort());
					}
					r = cmodule.floodlightRouting.getRoute(sw.getId(), ports[0].getSwitchDPID());
					dpid = ports[0].getSwitchDPID();
					port = ports[0].getPort();
			}
			else if(ports.length == 1){
				if(cmodule.debug_rpf){
					cmodule.println("\n");
					cmodule.println("Take The Only One AttachmentPoint: " + Long.toHexString(ports[0].getSwitchDPID()) + " - " + ports[0].getPort());
				}
				r = cmodule.floodlightRouting.getRoute(sw.getId(), ports[0].getSwitchDPID());
				dpid = ports[0].getSwitchDPID();
				port = ports[0].getPort();
			}
			
			
			if(r != null){
				path = r.getPath();
				if(path != null && path.size() != 0){
					if(cmodule.debug_rpf){
						cmodule.println("\n");
						cmodule.println("Founded Path Towards The Source: " + path);
						cmodule.println("PortIn=" + match.getInputPort() + " - Port Towards Source=" + path.get(0).getPortId());
					}
					
					if(sw.getId() == path.get(0).getNodeId() && path.get(0).getPortId() != match.getInputPort()){
						if(cmodule.debug_rpf){
							cmodule.println("\n");
							cmodule.println("RPF CHECK FAILED");
						}
						return false;
					}
					else if (sw.getId() != path.get(0).getNodeId()){
						cmodule.println("ERROR SWITCH ID NON COINCIDONO !!!");
						String a = null;
						a = a.toLowerCase();
					}
					
				}
				else{
					if(cmodule.debug_rpf){
						cmodule.println("\n");
						cmodule.println("No Valid Path Towards Source");
					}
				}
			}
			else{
				if(cmodule.debug_rpf){
					cmodule.println("\n");
					cmodule.println("No Valid Route Towards Source");
				}
			}
			
		}
		else{
			if(cmodule.debug_rpf){
				cmodule.println("\n");
				cmodule.println("No Valid Attachment Point");
			}
		}
		
		if(cmodule.debug_rpf){
			cmodule.println("\n");
			cmodule.println("Check If The AttachmentPoint Is The Switch That Receive Packet");
			cmodule.println("AttachmentPoint: DPID=" + Long.toHexString(dpid) + ", PORT=" + port);
		}
		
		if(cmodule.debug_rpf && dpid == sw.getId() && port != match.getInputPort()){
			cmodule.println("\n");
			cmodule.println("Directly Connected - RPF CHECK FAILED");
			return false;
		}
		
		
		if(cmodule.debug_rpf){
			cmodule.println("\n");
			cmodule.println("No Path Towards Source Or RPF Check Is OK - Call ConetListener");
		}
				
		return true;
	}

}
