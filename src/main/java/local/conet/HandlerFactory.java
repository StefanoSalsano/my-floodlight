package local.conet;

public class HandlerFactory {
	private static ConetMode current_mode = ConetMode.TBF;

	public static Handler getHandler(){
		Handler ret = null;
		switch(current_mode){
			case NOTBF:
				ret = new Handler();
				break;
			case TBF:
				ret = new HandlerMultiCS();
				break;
			case TBFF:
				ret = new HandlerMultiCSF();
				break;
			default:
				break;
		}
		return ret;
	}
	
	public static Handler getHandler(ConetMode mode){
		current_mode = mode;
		return getHandler();
	}
	
	public static ConetMode getCurrent_Mode(){
		return current_mode;
	}
}
