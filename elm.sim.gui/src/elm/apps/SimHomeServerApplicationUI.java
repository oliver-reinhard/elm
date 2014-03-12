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
import elm.sim.ui.AbstractSimServerApplicationConfiguration;
import elm.sim.ui.SimServerApplicationUI;
import elm.sim.ui.SimpleSimServerApplicationConfiguration;
import elm.util.ElmLogFormatter;

public class SimHomeServerApplicationUI {

	public static final int REGISTERED_SERVER_PORT = HomeServerService.INTERNAL_API_PORT;

	private static Logger LOG;

	public static void run(AbstractSimServerApplicationConfiguration configuration, String title, int width, int height) {
		try {
			ElmLogFormatter.init();
			LOG = Logger.getLogger(SimHomeServerApplicationUI.class.getName());

			final JmDNS jmDNS = JmDNS.create();
			final ServiceInfo serviceInfo = ServiceInfo.create(HomeServerService.DNS_SD_HS_SERVICE_TYPE, HomeServerService.DNS_SD_HS_SIM_SERVICE_NAME,
					REGISTERED_SERVER_PORT, "Home Server and ELM-Feedback Provider");
			
			configuration.init(false, REGISTERED_SERVER_PORT); // don't show SimpleSchedulerUI
			
			final SimServerApplicationUI ui = new SimServerApplicationUI(configuration);
			ui.setTitle(title);
			ui.setSize(width, height);
			ui.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			ui.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					ui.stop();
					info("unregistering...");
					jmDNS.unregisterService(serviceInfo);
					info("unregistered");
					System.exit(0); // EXIT!
				}
			});
			ui.setVisible(true);
			ui.start();

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

	public static void main(String[] args) {
		run(new SimpleSimServerApplicationConfiguration(), HomeServerService.DNS_SD_HS_SIM_SERVICE_NAME, 700, 600);
	}

	static void info(String action) {
		LOG.info("DNS Service Discovery: " + action);
	}

}
