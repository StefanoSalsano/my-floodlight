package local.conet;



import local.conet.ConetModule;

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

        // TAG-BASED FORWARDING: tbf_command=start|stop|info
        if (tbf_command!=null)
        {   if (tbf_command.equals("start")) conet_module.setTBF(true);
            else
            if (tbf_command.equals("stop")) conet_module.setTBF(false);

            TagBasedFW tag_based_fw=new TagBasedFW();
            tag_based_fw.tagbasedfw=((conet_module.getTBF())? "up" : "down");
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
        {   ConetModule.CachedContent[] cached_contents=conet_module.getCachedContents();     
            result.put(switch_id,cached_contents);
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
