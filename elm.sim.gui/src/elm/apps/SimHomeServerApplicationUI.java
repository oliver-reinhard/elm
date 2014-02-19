package elm.apps;

import elm.hs.api.sim.server.SimHomeServerImpl;
import elm.hs.api.sim.server.SimHomeServerServer;

public class SimHomeServerApplicationUI {

	public static void main(String[] args) throws Exception {

		SimpleSimDemoApplicationUI ui = SimpleSimDemoApplicationUI.createUI(false); // don't show SimpleSchedulerUI
		ui.setTitle("Home Server Simulation");
		ui.setSize(700, 600);
		ui.setVisible(true);

		SimHomeServerServer httpServer = new SimHomeServerServer((SimHomeServerImpl) ui.getServer());
		httpServer.start();
		// try {
		// JmDNS jmDNS = JmDNS.create();
		// ServiceInfo info = ServiceInfo.create("_clage-hs._tcp.local.", "Home Server Sim", 9090, "Home Server Simulation");
		// jmDNS.registerService(info);
		// System.out.println(HomeServerSimulation.class.getSimpleName() + " registered as Bonjour service.");
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		System.out.println(SimHomeServerDemoServer.class.getSimpleName() + " started.");
	}

}
