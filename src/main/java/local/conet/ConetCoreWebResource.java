package local.conet;



import local.conet.ConetModule;
import local.conet.ConetModule.CachedContent;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
//import org.restlet.resource.ResourceException;



/**
 * Return conet statistics information for specific switches.
 */
public class ConetCoreWebResource extends ServerResource
{    

    class CachedItems
    {   public int cacheditems;
    };


    class TagBasedFW
    {   public String tagbasedfw;
    };


    @Get("json")
    public Map<String, Object> retrieve()
    {   HashMap<String,Object> result=new HashMap<String,Object>();
        String switch_id=(String)getRequestAttributes().get("switchId");
        String stat_type=(String)getRequestAttributes().get("statType");
        //String tbf_command=(String)getRequestAttributes().get("tbfCommand");
        
        ConetModule conet_module=ConetModule.INSTANCE;

        if (switch_id.equals("all") && (stat_type.equals("start") || stat_type.equals("stop") || stat_type.equals("info")))
        {   
            if (stat_type.equals("start")) conet_module.setTBF(true);
            else
            if (stat_type.equals("stop")) conet_module.setTBF(false);

            TagBasedFW tag_based_fw=new TagBasedFW();
            tag_based_fw.tagbasedfw=((conet_module.getTBF())? "up" : "down");
            result.put(switch_id,tag_based_fw);
            return result;
        }
        else
        if (stat_type.equals("cacheditems"))
        {   CachedItems cached_items=new CachedItems();
            cached_items.cacheditems=conet_module.getCachedItems();     
            result.put(switch_id,cached_items);
            return result;
        }
        else
        if (stat_type.equals("cachedcontents"))
        {   //ConetModule.CachedContent[] cached_contents=conet_module.getCachedContents();     
       	    result  = conet_module.getCachedContents();
            return result;
        }
        // ONLY FOR TEST:
        else
        if (stat_type.equals("test"))
        {   result.put(switch_id,conet_module);
            return result;
        }
        // else
        result.put(switch_id,null);
        return result;
    }
}
