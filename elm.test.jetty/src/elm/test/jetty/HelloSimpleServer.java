package elm.test.jetty;

import org.eclipse.jetty.server.Server;

/** The simplest possible Jetty server.
 */
public class HelloSimpleServer {
 
    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        server.setHandler(new HelloHandler());
        server.start();
        server.join();
    }
}