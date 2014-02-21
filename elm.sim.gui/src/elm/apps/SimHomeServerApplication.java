package elm.apps;

import java.io.IOException;

import elm.hs.api.sim.server.SimHomeServer;
import elm.hs.api.sim.server.SimHomeServerImpl;
import elm.hs.api.sim.server.SimHomeServerServer;
import elm.util.ElmLogFormatter;


public class SimHomeServerApplication {

	public static void main(String[] args) throws Exception {
		try {
			ElmLogFormatter.init();
		} catch (SecurityException | IOException e) {
			System.exit(1);
		}
		
		SimHomeServer database = SimHomeServerImpl.createDemoDB("http://chs.local:9090");
		SimHomeServerServer server = new SimHomeServerServer(database);
		server.start();
		System.out.println(SimHomeServerApplication.class.getSimpleName() + " started.");
	}
}
