package local.conet;



//import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;
import net.floodlightcontroller.core.web.CoreWebRoutable;



/** Extends CoreWebRoutable for handling web URIs also for Conet module.
  */
public class ConetCoreWebRoutable extends CoreWebRoutable
{
   /** Gets Restlet for controller core and for Conet module. */
   @Override
   public Restlet getRestlet(Context context)
   {  Router router=(Router)super.getRestlet(context);
      // CONET - TAG-BASED FORWARDING REST-APIs:
      router.attach("/conet/{switchId}/{statType}/json", ConetCoreWebResource.class);
      router.attach("/cache-server/{switchId}/{statType}/json", ConetCoreWebResource.class);
      //router.attach("/switch/all/tagbasedfw/{tbfCommand}", ConetCoreWebResource.class);
      router.attach("/switch/{switchId}/tagbasedfw/{statType}", ConetCoreWebResource.class);
      return router;
   }
   
}
