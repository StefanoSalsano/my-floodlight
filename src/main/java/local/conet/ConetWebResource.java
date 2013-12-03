package local.conet;




import java.util.HashMap;
import java.util.Map;

import local.conet.ConetCoreWebResource.CachedItems;
import local.conet.ConetCoreWebResource.TagBasedFW;

import org.restlet.data.Form;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
//import org.restlet.resource.ResourceException;



/**
 * Conet API for REST interface.
 */
public class ConetWebResource extends ServerResource
{    

    class CachedItems
    {   public int cacheditems;
    };


    class TagBasedFW
    {   public String tagbasedfw;
    };

    
    @Get("json")
    public Map<String, Object> retrieve() {
    	
    	HashMap<String,Object> result=new HashMap<String,Object>();
        String switch_id=(String)getRequestAttributes().get("switchId");
        String stat_type=(String)getRequestAttributes().get("tbfCommand");
        Form r = this.getQuery();
        ConetModule conet_module=ConetModule.INSTANCE;
        
        if (switch_id.equalsIgnoreCase("all") && (stat_type.equalsIgnoreCase("tagexpire") || stat_type.equalsIgnoreCase("tbff") || stat_type.equalsIgnoreCase("tbf") ||
        	    	stat_type.equalsIgnoreCase("notbf") || stat_type.equalsIgnoreCase("info"))) {
        	
        	if(!conet_module.debug_learning_switch_only){
	            if (stat_type.equalsIgnoreCase("tbff")) 
	            	conet_module.setTBF(ConetMode.TBFF);
	            else if (stat_type.equalsIgnoreCase("tbf"))
	            	conet_module.setTBF(ConetMode.TBF);
	            else if(stat_type.equalsIgnoreCase("notbf"))
	            	conet_module.setTBF(ConetMode.NOTBF);
	            else if(stat_type.equalsIgnoreCase("tagexpire")){
	            	String s = r.getFirstValue("value");
    				if(s!=null){
    					conet_module.CONET_IDLE_TIMEOUT = Short.parseShort(s);
    					conet_module.setTBF(conet_module.getTBF());
    					System.out.println(conet_module.CONET_IDLE_TIMEOUT);
        			}
	            }
        	}
            TagBasedFW tag_based_fw=new TagBasedFW();
            tag_based_fw.tagbasedfw=conet_module.getTBF().name();
            result.put(switch_id,tag_based_fw);
            return result;
        } else if (stat_type.equals("cacheditems")) {
        	CachedItems cached_items=new CachedItems();
            cached_items.cacheditems=conet_module.getCachedItems();     
            result.put(switch_id,cached_items);
            return result;
        } else if (stat_type.equals("cacheditemsmap")) {
       	    result  = conet_module.getCachedItemsMap();
            return result;
        } else if ( stat_type.equals("cachedcontents")) {  
        	//ConetModule.CachedContent[] cached_contents=conet_module.getCachedContents();     
       	    result  = conet_module.getCachedContents();
            return result;
        } else if ( stat_type.equals("cacheserver")){
        	result.put("seen_cache_server", conet_module.seen_cache_server);
        	return result;
        }else if (stat_type.equals("test")){   
        	// ONLY FOR TEST:
        	result.put(switch_id,conet_module);
            return result;
        }
        // else
        result.put(switch_id,null);
        return result;
    }


//
//    @Get("json")
//    public Map<String, Object> retrieve()
//    {   
//    	System.out.println(("Call ConetWeb"));
//
//    	Map attributes=getRequestAttributes();
//        String switch_id=(String)attributes.get("switchId");
//        String stat_type=(String)attributes.get("statType");
//        String tbf_command=(String)attributes.get("tbfCommand");
//        
//        HashMap<String,Object> result=new HashMap<String,Object>();
//        ConetModule conet_module=ConetModule.INSTANCE;
//
//        // TAG-BASED FORWARDING: tbf_command=TBFF|TBF|NOTBF|INFO
//        if(tbf_command!=null)
//        {   
//        	if(!conet_module.debug_learning_switch_only){
//	        	if(tbf_command.equalsIgnoreCase("TBFF")) 
//	        		conet_module.setTBF(ConetMode.TBFF);
//	            else if(tbf_command.equals("TBF")) 
//	            	conet_module.setTBF(ConetMode.TBF);
//	            else if(tbf_command.equals("NOTBF"))
//	            	conet_module.setTBF(ConetMode.NOTBF);
//        	}
//            TagBasedFW tag_based_fw=new TagBasedFW();
//            tag_based_fw.tagbasedfw=conet_module.getTBF().name();
//            result.put("all",tag_based_fw);
//            return result;
//        }
//        else
//        // CACHE-SERVER statistics: stat_type=cacheditems|cachedcontents
//        if (stat_type!=null && stat_type.equals("cacheditems"))
//        {   CachedItems cached_items=new CachedItems();
//            cached_items.cacheditems=conet_module.getCachedItems();     
//            result.put(switch_id,cached_items);
//            return result;
//        }
//        else
//        if (stat_type!=null && stat_type.equals("cachedcontents"))
//        {   //ConetModule.CachedContent[] cached_contents=conet_module.getCachedContents();
//        	result = conet_module.getCachedContents();
//            return result;
//        }
//        else
//        // ONLY FOR TEST:
//        if (stat_type!=null && stat_type.equals("test"))
//        {   result.put(switch_id,conet_module);
//            return result;
//        }
//        // else
//        //result.put(switch_id,null);
//        return result;
//    }

}
