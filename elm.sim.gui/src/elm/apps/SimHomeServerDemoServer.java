package elm.apps;

import elm.hs.api.sim.server.SimHomeServer;
import elm.hs.api.sim.server.SimHomeServerServer;


public class SimHomeServerDemoServer {

	public static void main(String[] args) throws Exception {
		// try {
		// JmDNS jmDNS = JmDNS.create();
		// ServiceInfo info = ServiceInfo.create("_clage-hs._tcp.local.", "Home Server Sim", 9090, "Home Server Simulation");
		// jmDNS.registerService(info);
		// System.out.println(HomeServerSimulation.class.getSimpleName() + " registered as Bonjour service.");
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		SimHomeServer database = SimHomeServer.createDemoDB("http://chs.local:9090");
		SimHomeServerServer server = new SimHomeServerServer(database);
		server.start();
		System.out.println(SimHomeServerDemoServer.class.getSimpleName() + " started.");
	}
}
