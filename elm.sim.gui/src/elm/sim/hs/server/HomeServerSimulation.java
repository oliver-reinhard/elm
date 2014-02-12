package elm.sim.hs.server;

import java.io.IOException;
import java.net.URI;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import elm.hs.api.model.HomeServerResponse;

public class HomeServerSimulation {

	@SuppressWarnings("serial")
	class StatusServlet extends AbstractHomeServerServlet {
		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			return getDatabase().getStatus();
		}
	}

	@SuppressWarnings("serial")
	class DevicesServlet extends AbstractHomeServerServlet {

		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			return getDatabase().getDevices();
		}
	}

	@SuppressWarnings("serial")
	class DeviceStatusServlet extends AbstractHomeServerServlet {

		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			String uri = request.getRequestURI();
			String[] segments = uri.split("/");
			return getDatabase().getDeviceStatus(segments[segments.length - 1]);
		}
	}

	@SuppressWarnings("serial")
	class DeviceSetpointServlet extends AbstractHomeServerServlet {

		/**
		 * Parses a request of {@code /devices/setpoint/<id>} with either a request content of {@code data=<iii>} (in 10ths of a degree) to set the new value,
		 * or without request content to query the current value.
		 */
		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			final String path = request.getPathInfo();
			String id = path.startsWith("/") ? path.substring(1) : path;
			byte[] buf = new byte[10];
			try {
				ServletInputStream stream = request.getInputStream();
				int len = stream.read(buf);
				if (len > 0) {
					String data = new String(buf, 0, len);
					if (data.startsWith("data=")) {
						String temperatureStr = data.substring(5);
						short temperature = Short.parseShort(temperatureStr);
						return getDatabase().deviceSetpoint(id, temperature);
					}
				} else {
					return getDatabase().getDeviceStatus(id);
				}
			} catch (NumberFormatException | IOException e) {
				LOG.info("Unexpected request data: \"" + buf + "\"", e);
			}
			return null;
		}
	}

	private final Server server;
	private final HomeServerDB database;

	public HomeServerSimulation(URI uri) {
		database = new HomeServerDB(uri.toString());

		server = new Server(uri.getPort());

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.addServlet(new ServletHolder(new StatusServlet()), "/");
		context.addServlet(new ServletHolder(new DevicesServlet()), "/devices");
		context.addServlet(new ServletHolder(new DeviceStatusServlet()), "/devices/status/*");
		context.addServlet(new ServletHolder(new DeviceSetpointServlet()), "/devices/setpoint/*");

		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[] { /* resource_handler, */context, new DefaultHandler() });

		server.setHandler(handlers);
	}

	protected HomeServerDB getDatabase() {
		return database;
	}

	public void start() throws Exception {
		server.start();
		server.join();
	}

	public static void main(String[] args) throws Exception {
		try {
			JmDNS jmDNS = JmDNS.create();
			ServiceInfo info = ServiceInfo.create("_clage-hs._tcp.local.", "Home Server Sim", 9090, "Home Server Simulation");
			jmDNS.registerService(info);
			System.out.println(HomeServerSimulation.class.getSimpleName() + " registered as Bonjour service.");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HomeServerSimulation server = new HomeServerSimulation(URI.create("http://chs.local:9090"));
		server.start();
		System.out.println(HomeServerSimulation.class.getSimpleName() + " started.");
		
	}
}
