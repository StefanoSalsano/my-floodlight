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

}
