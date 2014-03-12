package elm.apps;

import java.io.IOException;

import elm.hs.api.sim.server.SimHomeServerService;
import elm.hs.api.sim.server.SimHomeServerServiceImpl;
import elm.hs.api.sim.server.SimHomeServerServer;
import elm.util.ElmLogFormatter;


public class SimHomeServerApplication {

	public static void main(String[] args) throws Exception {
		try {
			ElmLogFormatter.init();
		} catch (SecurityException | IOException e) {
			System.exit(1);
		}
		
		SimHomeServerService database = SimHomeServerServiceImpl.createDemoDB("http://chs.local:9090");
		SimHomeServerServer server = new SimHomeServerServer(database);
		server.start();
		System.out.println(SimHomeServerApplication.class.getSimpleName() + " started.");
	}
}
