package elm.hs.api.sim.server;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.hs.api.ElmUserFeedback;
import elm.hs.api.HomeServerResponse;
import elm.sim.model.TapPoint;

/**
 * This class is an executable HTTP emulation of the CLAGE Home Server that is based on simulated {@link TapPoint}s.
 */
public class SimHomeServerServer {

	@SuppressWarnings("serial")
	class StatusServlet extends AbstractHomeServerServlet {
		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			return getDatabase().processStatusQuery();
		}
	}

	@SuppressWarnings("serial")
	class DevicesServlet extends AbstractHomeServerServlet {

		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			return getDatabase().processDevicesQuery();
		}
	}

	@SuppressWarnings("serial")
	class DeviceStatusServlet extends AbstractHomeServerServlet {

		/**
		 * Parses a request of {@code /devices/status} or {@code /devices/status/<id>}.
		 */
		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			String uri = request.getRequestURI();
			String[] segments = uri.split("/");
			final String deviceID = segments[segments.length - 1];
			return getDatabase().processDeviceStatusQuery(deviceID);
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
			try {
				Short referenceTemperature = extractShort(request, id, true, log); // optional
				if (referenceTemperature != null) {
					return getDatabase().processDeviceSetpoint(id, referenceTemperature);
				} else {
					return getDatabase().processDeviceStatusQuery(id);
				}
			} catch (IOException | IllegalArgumentException e) {
				// already logged
			}
			return null;
		}
	}

	@SuppressWarnings("serial")
	class SetScaldProtectionServlet extends AbstractHomeServerServlet {

		/**
		 * Parses a request of {@code /cmd/Vv/<id>} with a request content of {@code data=<iii>} (in <em>full</em> of a degree) to set the new value, or without
		 * request content to query the current value.
		 */
		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			final String path = request.getPathInfo();
			String id = path.startsWith("/") ? path.substring(1) : path;
			try {
				short scaldProtectionTemperature = extractShort(request, id, false, log); // mandatory
				// scald protection value is in FULL DEGREES Celcius!
				return getDatabase().processSetScaldProtectionTemperature(id, (short) (scaldProtectionTemperature * 10));
			} catch (IOException | IllegalArgumentException e) {
				// already logged
			}
			return null;
		}
	}

	@SuppressWarnings("serial")
	class ClearScaldProtectionServlet extends AbstractHomeServerServlet {

		/**
		 * Parses a request of {@code /cmd/VF/<id>} with a request content of {@code data=<i>} ({@code 0}=off, {@code 1}=on) to set the new value.
		 */
		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			final String path = request.getPathInfo();
			String id = path.startsWith("/") ? path.substring(1) : path;
			try {
				short value = extractShort(request, id, false, log); // mandatory
				return getDatabase().processSetScaldProtectionFlag(id, value == 1);
			} catch (IOException | IllegalArgumentException e) {
				// already logged
			}
			return null;
		}
	}

	@SuppressWarnings("serial")
	class DeviceFeedbackServlet extends AbstractHomeServerServlet {

		private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

		/**
		 * Parses a POST request of {@code /devices/feedback} with a content of one JSON'ed {@link List} of {@link ElmUserFeedback}.
		 */
		@Override
		protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			ServletInputStream stream = request.getInputStream();
			byte[] buf = new byte[stream.available()];
			try {
				int len = stream.read(buf);
				if (len > 0) {
					String requestAsString = new String(buf, 0, len);
					final List<ElmUserFeedback> feedback = gson.fromJson(requestAsString, ElmUserFeedback.ELM_USER_FEEDBACK_LIST_TYPE);
					if (feedback != null) {
						database.processUserFeedback(feedback);
						response.setStatus(HttpStatus.OK_200);
						return;
					}
				}
			} catch (IOException e) {
				log.log(Level.SEVERE, "Unexpected request data: \"" + buf + "\"", e);
			}
			response.sendError(HttpStatus.BAD_REQUEST_400, "List of ElmDeviceUserFeedback expected");
		}

		/**
		 * Processes a GET request of {@code /devices/feedback} without parameters or request body.
		 */
		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			// copied from super.doPost(..):
			try {
				HomeServerResponse homeServerResponse = getHomeServerResponse(request);
				sendSingleMessage(response, homeServerResponse);
			} catch (RuntimeException e) {
				e.printStackTrace();
				response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500, "Server error: " + e.getMessage());
			}
		}

		@Override
		protected HomeServerResponse getHomeServerResponse(HttpServletRequest request) {
			return getDatabase().processDevicesFeedbackQuery();
		}
	}

	private final Server server;
	private final SimHomeServerService database;
	protected final Logger log = Logger.getLogger(getClass().getName());

	public SimHomeServerServer(SimHomeServerService database) {
		assert database != null;
		assert !database.getDevices().isEmpty();
		this.database = database;

		server = new Server(database.getUri().getPort());

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.addServlet(new ServletHolder(new StatusServlet()), "/");
		context.addServlet(new ServletHolder(new DevicesServlet()), "/devices");
		context.addServlet(new ServletHolder(new DeviceStatusServlet()), "/devices/status/*");
		context.addServlet(new ServletHolder(new DeviceSetpointServlet()), "/devices/setpoint/*");
		context.addServlet(new ServletHolder(new SetScaldProtectionServlet()), "/cmd/Vv/*");
		context.addServlet(new ServletHolder(new ClearScaldProtectionServlet()), "/cmd/VF/*");
		context.addServlet(new ServletHolder(new DeviceFeedbackServlet()), "/devices/feedback");

		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[] { /* resource_handler, */context, new DefaultHandler() });

		server.setHandler(handlers);
	}

	protected SimHomeServerService getDatabase() {
		return database;
	}

	public void start() throws Exception {
		server.start();
		log.log(Level.INFO, "HomeServer started: " + server.getURI());
	}

	public void processCalls() throws Exception {
		server.join(); // blocking
	}
}
