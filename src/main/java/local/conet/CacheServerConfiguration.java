package local.conet;

public class CacheServerConfiguration {
	private String sw_datapath;
	private String cache_ip_addr;
	private String cache_mac_addr;
	private Integer cache_port;
	private String sw_virtual_mac_addr;
//	private Pair<String,Integer> home_server_range;
	
	public CacheServerConfiguration(Long sw, String cache_ip, String cache_mac, Integer cache_port, String sw_virtual_mac){
		this.sw_datapath = ConetUtility.dpLong2String(sw);
		this.cache_ip_addr = cache_ip;
		this.cache_mac_addr = cache_mac;
		this.cache_port = cache_port;
		this.sw_virtual_mac_addr = sw_virtual_mac;
//		this.home_server_range = ConetUtility.getPrefixFromString(home_server);
	}
	
	
	public String getSW(){
		return this.sw_datapath;
	}
	
	public String getCacheIPAddr(){
		return this.cache_ip_addr;
	}
	
	public String getCacheMACAddr(){
		return this.cache_mac_addr;
	}
	
	public Integer getCachePort(){
		return this.cache_port;
	}
	
	public String getSWAddr(){
		return this.sw_virtual_mac_addr;
	}
	
//	public String getHomeServerNetMask(){
//		return this.home_server_range.first;
//	}
	
//	public Integer getHomeServerBitMask(){
//		return this.home_server_range.second;
//	}
	
	@Override
	public String toString(){
		return "[" + this.sw_datapath + ", " + this.cache_ip_addr + ", " + this.cache_mac_addr + ", "
				+ this.cache_port + ", " + this.sw_virtual_mac_addr + "]";
	}
	

}
