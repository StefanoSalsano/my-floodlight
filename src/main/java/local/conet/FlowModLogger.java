package local.conet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;import org.zoolu.tools.Log;
import org.zoolu.tools.RotatingLog;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;

public class FlowModLogger implements IOFMessageListener{
	private Log log;
	
	
	public FlowModLogger(String log_file, int log_size, int log_rotation, int log_time){
		if (log == null) {
			log = new RotatingLog(log_file, 1, (((long) log_size) * 1024) * 1024, log_rotation, RotatingLog.DAY,
					log_time);
			log.setTimestamp(true);
		}
	}
	

	@Override
	public String getName() {
		// Auto-generated method stub
		return "FlowModLogger";
	}
	
	public String getPrintName(){
		return "[" + Thread.currentThread().getName() + "] - FlowModLogger - ";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// Auto-generated method stub
		return false;
	}

	@Override
	public Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// Auto-generated method stub
		this.println("Receive FlowMod Notification -  " + sw + " - " + msg);
		return Command.CONTINUE;
	}
	
	/** Prints a log message. */
	private void println(String str) {
		if (log != null)
			log.println(this.getPrintName() + str + "\n\n");
		System.out.println(this.getPrintName() + str + "\n\n");
	}

}




/*
 * 
 * XXX UNUSED CODE
 * 
 */


///** Prints the Exception. */
//private void printException(Exception e) {
//	println(this.getName() + "Exception: " + ExceptionPrinter.getStackTraceOf(e) + "\n\n" );
//}
//
///** Prints a blank line. */
//private void println() {
//	if (log != null)
//		log.println(this.getName()+ "\n\n");
//	println(this.getName() + "\n\n");
//}
