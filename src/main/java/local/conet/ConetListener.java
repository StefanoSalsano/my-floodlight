package local.conet;

import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFType;
import org.zoolu.net.message.Message;
import org.zoolu.net.message.MsgTransport;
import org.zoolu.net.message.MsgTransportListener;
import org.zoolu.tools.BinAddrTools;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;

public class ConetListener implements IOFMessageListener, MsgTransportListener{

	@Override
	public String getName() {
		return "ConetListener";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Command ret = Command.CONTINUE;
		ConetModule conet = ConetModule.INSTANCE;
		if(conet.debug_multi_cs)
			conet.println("CALL RECEIVE");
		if(conet.debug_multi_cs)
			conet.println("MSG: " + msg);
		try{
			Long dp = Long.valueOf(sw.getId());
			if (!conet.seen_switches.containsKey(dp)) {
				if(conet.debug_multi_cs)
					conet.println("LEARN NEW SW: " + sw.getId());
				conet.seen_switches.put(dp, sw);
				conet.doFlowModDeleteAll(sw, (short) ConetModule.VLAN_ID);
				if(conet.static_ghent){
					long ghent_id = Long.parseLong(BinAddrTools.trimHexString("01:00:00:00:00:00:00:FF"),16);
					if(ghent_id == sw.getId()){
						conet.initGhent(sw);
					}
				}
					
			}
		}
		catch(Exception e){
			conet.println("WARNING: receive(): sw.getId() failed.");
			conet.printException(e);
		}
		
		Handler h = HandlerFactory.getHandler();
		if(conet.debug_multi_cs)
			conet.println("USE Handler: " + HandlerFactory.getCurrent_Mode().name());
		
		if (msg.getType() == OFType.PACKET_IN) {
			if(conet.debug_multi_cs)
				conet.println("Arrive PACKET_IN");
			ret =  h.processConetPacketInMessage(sw, (OFPacketIn) msg, cntx);
		}
		else if (msg.getType() == OFType.PORT_STATUS){
			if(conet.debug_multi_cs)
				conet.println("Arrive PORT_STATUS");
			ret = h.processPortStatusMessage(sw, (OFPortStatus) msg);
		}
		else if (msg.getType() == OFType.FLOW_REMOVED){
			if(conet.debug_multi_cs)
				conet.println("Arrive Flow_Removed");
			ret = h.processFlowRemovedMessage(sw, (OFFlowRemoved) msg);
		}
		else if (msg.getType() == OFType.HELLO){
			if(conet.debug_multi_cs)
				conet.println("Arrive Hello");
			ret =  h.processHelloMessage(sw, (OFHello) msg);
		}
		else if (msg.getType() == OFType.ERROR) {
			if(conet.debug_multi_cs)
				conet.println("Received from switch " + sw + " the error: " + msg);
		}
		else{
			conet.println("Received from switch " + sw + " the unexpected msg: " + msg);
		}
		return ret;
		
		
	}

	public ConetMode getMode() {
		return HandlerFactory.getCurrent_Mode();
	}
	
	public void changeMode(ConetMode newMode){
		ConetModule conet = ConetModule.INSTANCE;
		Handler h = HandlerFactory.getHandler();
		conet.println("DISABLE THE MODE: " + this.getMode().name());
		if(this.getMode() != ConetMode.NOTBF)
			h.deleteAllConetRule();
		conet.println("ACTIVATE THE MODE: " + newMode.name());
		h = HandlerFactory.getHandler(newMode);
		if(newMode != ConetMode.NOTBF)
			h.populateAllConetRule();
	}

	@Override
	public void onReceivedMessage(MsgTransport transport, Message msg) {
		ConetModule conet = ConetModule.INSTANCE;
		Handler h = HandlerFactory.getHandler();
		if(conet.debug_multi_cs)
			conet.println("USE Handler: " + HandlerFactory.getCurrent_Mode().name());
		h.processCacheServerMessage(transport, msg);
	}

	@Override
	public void onMsgTransportTerminated(MsgTransport transport, Exception error) {
		// TODO Auto-generated method stub
		
	}
	
	
	
	
	
//	@Override
//	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
//		/*if (LEARNING_SWITCH_VERBOSE)
//			println("receive(): " + msg);*/
//		if(this.debug_multi_cs)
//			println("receive(): " + msg);
//		
//		if(msg.getType() == OFType.FLOW_MOD)
//			return Command.CONTINUE;
//		
//		// LEARN DATAPATH-TO-SWITCH MAPPING AND DELETE ALL FLOW TABLE ENTRIES
//		try {
//			Long dp = Long.valueOf(sw.getId());
//			if (!seen_switches.containsKey(dp)) { 
//				//if the switch is not in our DB it is inserted and
//				//all flows are cleaned
//				
//				seen_switches.put(dp, sw);
//				doFlowModDeleteAll(sw, (short) VLAN_ID);
//				// DEBUG: use also the following alternative method
//				//testDeleteAllFlowTableEntries();
//			}
//		} catch (Exception e) {
//			println("WARNING: receive(): sw.getId() failed.");
//		}
//		;
//
//		// testFlowModStatic(sw);
//
//		if (msg.getType() == OFType.PACKET_IN) {
//			if (debug_learning_switch_only || !tag_based_forwarding)
//				return processPacketInMessage(sw, (OFPacketIn) msg, cntx);
//			else
//				return processConetPacketInMessage(sw, (OFPacketIn) msg, cntx);
//		}
//		// else
//		if (msg.getType() == OFType.PORT_STATUS)
//			return processPortStatusMessage(sw, (OFPortStatus) msg);
//		// else
//		if (msg.getType() == OFType.FLOW_REMOVED)
//			return processFlowRemovedMessage(sw, (OFFlowRemoved) msg);
//		// else
//		if (msg.getType() == OFType.HELLO)
//			return processHelloMessage(sw, (OFHello) msg);
//		// else
//		if (msg.getType() == OFType.ERROR) {
//			//if (LEARNING_SWITCH_VERBOSE)
//				println("LearningSwitch: receive(): received from switch " + sw + " the error: " + msg);
//			return Command.CONTINUE;
//		}
//		// else
//		//if (LEARNING_SWITCH_VERBOSE)
//			println("LearningSwitch: receive(): received from switch " + sw + " the unexpected msg: " + msg);
//		return Command.CONTINUE;
//
//	}
	
	
	

}
