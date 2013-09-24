package local.conet;



import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;
//import net.floodlightcontroller.core.web.CoreWebRoutable;



/** Creates a router to handle web URIs for Conet module.
  */
public class ConetWebRoutable implements RestletRoutable
{
   /** Core web route */
   //CoreWebRoutable core_web_routable=new CoreWebRoutable();


   /** Gets base path. */
   @Override
   public String basePath()
   {  //return core_web_routable.basePath();
      return "/icn";
   }
   

   /** Gets Restlet for Conet module. */
   @Override
   public Restlet getRestlet(Context context)
   {  // inherit paths of the core web route
      //Router router=(Router)core_web_routable.getRestlet(context);
      Router router=new Router(context);

      //router.attach("/switch/{switchId}/{statType}/json", ConetWebResource.class);
      
      // TAG-BASED FORWARDING:
      router.attach("/switch/all/tagbasedfw/{tbfCommand}", ConetWebResource.class);
      // tbfCommand=start|stop|info

      // CACHE-SERVER statistics:
      router.attach("/cache-server/{switchId}/{statType}/json", ConetWebResource.class);
      // switchId=<cache-mac-addr>
      // statType=cacheditems|cachedcontents

      return router;
   }
   
}
