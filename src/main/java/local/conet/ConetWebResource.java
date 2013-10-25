package local.conet;




import java.util.HashMap;
import java.util.Map;

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
    public Map<String, Object> retrieve()
    {   Map attributes=getRequestAttributes();
        String switch_id=(String)attributes.get("switchId");
        String stat_type=(String)attributes.get("statType");
        String tbf_command=(String)attributes.get("tbfCommand");
        
        HashMap<String,Object> result=new HashMap<String,Object>();
        ConetModule conet_module=ConetModule.INSTANCE;

        // TAG-BASED FORWARDING: tbf_command=TBFF|TBF|NOTBF|INFO
        if(tbf_command!=null)
        {   
        	if(!conet_module.debug_learning_switch_only){
	        	if(tbf_command.equalsIgnoreCase("TBFF")) 
	        		conet_module.setTBF(ConetMode.TBFF);
	            else if(tbf_command.equals("TBF")) 
	            	conet_module.setTBF(ConetMode.TBF);
	            else if(tbf_command.equals("NOTBF"))
	            	conet_module.setTBF(ConetMode.NOTBF);
        	}
            TagBasedFW tag_based_fw=new TagBasedFW();
            tag_based_fw.tagbasedfw=conet_module.getTBF().name();
            result.put("all",tag_based_fw);
            return result;
        }
        else
        // CACHE-SERVER statistics: stat_type=cacheditems|cachedcontents
        if (stat_type!=null && stat_type.equals("cacheditems"))
        {   CachedItems cached_items=new CachedItems();
            cached_items.cacheditems=conet_module.getCachedItems();     
            result.put(switch_id,cached_items);
            return result;
        }
        else
        if (stat_type!=null && stat_type.equals("cachedcontents"))
        {   //ConetModule.CachedContent[] cached_contents=conet_module.getCachedContents();
        	result = conet_module.getCachedContents();
            return result;
        }
        else
        // ONLY FOR TEST:
        if (stat_type!=null && stat_type.equals("test"))
        {   result.put(switch_id,conet_module);
            return result;
        }
        // else
        //result.put(switch_id,null);
        return result;
    }
}
