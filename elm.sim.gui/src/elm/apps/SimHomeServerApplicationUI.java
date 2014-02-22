package elm.apps;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.swing.JFrame;

import elm.hs.api.HomeServerService;
import elm.hs.api.sim.server.SimHomeServerServer;
import elm.sim.ui.SimServerApplicationUI;
import elm.sim.ui.SimpleSimServerApplicationConfiguration;
import elm.util.ElmLogFormatter;

public class SimHomeServerApplicationUI {

	public static final int REGISTERED_SERVER_PORT = 9090;

	private static Logger LOG;

	public static void main(String[] args) {
		try {
			ElmLogFormatter.init();
			LOG = Logger.getLogger(SimHomeServerApplicationUI.class.getName());

			final JmDNS jmDNS = JmDNS.create();
			final ServiceInfo serviceInfo = ServiceInfo.create(HomeServerService.DNS_SD_HS_SERVICE_TYPE, HomeServerService.DNS_SD_HS_SIM_SERVICE_NAME,
					REGISTERED_SERVER_PORT, "Home Server and ELM-Feedback Provider");

			SimpleSimServerApplicationConfiguration configuration = new SimpleSimServerApplicationConfiguration();
			configuration.init(false, REGISTERED_SERVER_PORT); // don't show SimpleSchedulerUI
			SimServerApplicationUI ui = new SimServerApplicationUI(configuration);
			ui.setTitle(HomeServerService.DNS_SD_HS_SIM_SERVICE_NAME);
			ui.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			ui.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					info("unregistering...");
					jmDNS.unregisterService(serviceInfo);
					info("unregistered");
					System.exit(0); // EXIT!
				}
			});
			ui.setVisible(true);

			SimHomeServerServer httpServer = new SimHomeServerServer(configuration.getServer());
			httpServer.start();

			info("registering (service type: " + HomeServerService.DNS_SD_HS_SERVICE_TYPE + ") ...");
			jmDNS.registerService(serviceInfo);
			info("registered");

			httpServer.processCalls(); // blocking

		} catch (Exception e) {
			LOG.log(Level.SEVERE, HomeServerService.DNS_SD_HS_SIM_SERVICE_NAME + " start failed", e);
			System.exit(1);
		}
	}

	static void info(String action) {
		LOG.info("DNS Service Discovery: " + action);
	}

}
