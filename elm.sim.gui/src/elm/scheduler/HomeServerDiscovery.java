package elm.scheduler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import elm.hs.api.HomeServerService;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.impl.HomeServerImpl;

/**
 * This class listens for DNS Service Discovery events for CLAGE Home Servers. Whenever a new Home Server instance is detected, a new
 * {@link HomeServerController} is started and the scheduler is notified.
 */
public class HomeServerDiscovery implements ServiceListener {

	private static final Logger LOG = Logger.getLogger(HomeServerDiscovery.class.getName());

	private final AbstractElmScheduler scheduler;
	private final ElmUserFeedbackManager userFeedbackManager;
	private final String homeServerPassword;

	private JmDNS jmDNS;

	public HomeServerDiscovery(AbstractElmScheduler scheduler, String homeServerPassword) {
		assert scheduler != null;
		assert homeServerPassword != null;
		this.scheduler = scheduler;
		this.userFeedbackManager = new ElmUserFeedbackManager();
		this.homeServerPassword = homeServerPassword;
	}

	@Override
	public void serviceAdded(ServiceEvent e) {
		info("added", e);
		jmDNS.requestServiceInfo(HomeServerService.DNS_SD_HS_SERVICE_TYPE, e.getName()); // this prompts a serviceResolved() notification a.s.a.p.
	}

	@Override
	public void serviceResolved(ServiceEvent e) {
		info("resolved", e);
		final String[] urls = e.getInfo().getURLs("https");
		if (urls.length > 0) {
			for (String url : urls) {
				try {
					URI uri = new URI(url);
					// The real CLAGE Home Servers require https, but the Sim Home Servers require http:
					if (e.getName().equals(HomeServerService.DNS_SD_HS_SIM_SERVICE_NAME)) {
						uri = new URI(url.toLowerCase().replace("https", "http"));
					}
					HomeServer homeServer = new HomeServerImpl(uri, homeServerPassword, userFeedbackManager);
					HomeServerController controller = new HomeServerController(scheduler, userFeedbackManager, homeServer);
					LOG.info("Starting new " + controller.getClass().getSimpleName() + " for '" + e.getName() + "' at " + uri);
					controller.start();

				} catch (URISyntaxException ex) {
					LOG.log(Level.WARNING, "Invalid URL of registered Home Server: " + url, ex);
				}
			}
		} else {
			LOG.severe("Registered Home Server provides no host address. No " + HomeServerController.class.getSimpleName() + " started");
		}
	}

	@Override
	public void serviceRemoved(ServiceEvent e) {
		info("removed", e);
	}

	void info(String action, ServiceEvent e) {
		final String[] urls = e.getInfo().getURLs("https");
		final String url = urls.length > 0 ? urls[0] : "(unknown URL)";
		LOG.info("Received discovery notification: Service " + action + ": " + e.getType() + ", " + e.getName() + " at " + url);
	}

	public void start() throws IOException {
		LOG.info("Starting service listener for service type '" + HomeServerService.DNS_SD_HS_SERVICE_TYPE + "'");
		jmDNS = JmDNS.create();
		jmDNS.addServiceListener(HomeServerService.DNS_SD_HS_SERVICE_TYPE, this);
	}
}
