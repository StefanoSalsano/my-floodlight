package local.conet;

public class ConetUtility {
	
	/**
	 * This is needed to convert a dataPath long into an Hex string
	 * with no colon, preserving leading '0's (it will be 16 hexadecimal characters
	 * as the dataPath is 8 bytes)
	 * 
	 * @param dpLong
	 * @return
	 */
	public static String dpLong2String (long dpLong) {
		String temp = Long.toHexString(dpLong);
		int len = temp.length();
		return (("0000000000000000"+temp).substring(len));
	}
	
	public static Pair<String,Integer> getPrefixFromString(String prefix){
		String[] st = prefix.split("\\/");
		 return new Pair<String , Integer>(st[0],Integer.parseInt(st[1]));
	}
	
	public static String addColonToMac(String inMac) {
		final StringBuilder b = new StringBuilder(18);
		for (int i = 0; i < inMac.length(); i++) {
		  b.append(inMac.charAt(i));
		  if (i%2 == 1 && i != inMac.length()-1) b.append(':');
		}
		return b.toString();
	}
	
	public static String fixMac(Long inMac){
		String temp = "";
		temp = Long.toHexString(inMac);
		int size = temp.length();
		int i = 0;
		while(i < (12-size)){
			temp = "0" + temp;
			i++;
		}
		return temp;
	}

}
