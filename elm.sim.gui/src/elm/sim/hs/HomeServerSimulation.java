package elm.sim.hs;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;


public class HomeServerSimulation {
 
    public static void main(String[] args) throws Exception {

        Server server = new Server(8080);

        ResourceHandler resource_handler = new ResourceHandler();
        // Configure the ResourceHandler. Setting the resource base indicates where the files should be served out of.
        // Use the current directory (can be configured to anything that the jvm has access to)
        resource_handler.setDirectoriesListed(true);
       // resource_handler.setWelcomeFiles(new String[]{ "index.html" });
        resource_handler.setResourceBase(".");

    	final HomeServerDB database = new HomeServerDB();
		HomeServerDB.addMockData(database);
    	
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new DevicesServlet(database)), "/hs/*");
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { resource_handler, context, new DefaultHandler() });
        
        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
