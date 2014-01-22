package elm.sim.hs;

import java.io.IOException;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.sim.hs.model.Device;

@SuppressWarnings("serial")
public class DevicesServlet extends HttpServlet {

	static final Logger LOG = Log.getLogger(DevicesServlet.class);

	private final HomeServerDB database;

	public DevicesServlet(HomeServerDB database) {
		this.database = database;
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		sendSingleMessage(response, database.getDevices());
	}

	private void sendSingleMessage(HttpServletResponse response, List<Device> devices) throws IOException {
		response.setContentType("text/json;charset=utf-8");
		Gson gson = new GsonBuilder().create(); // new GsonBuilder().setPrettyPrinting().create();
		String str = gson.toJson(devices.get(0));
		LOG.info("Sending FIRST Device: " + str);
		response.getWriter().println(str);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (request.getParameter("action") != null)
			doPost(request, response);
		else {
			ServletContext context = getServletContext();
			RequestDispatcher dispatcher = context.getNamedDispatcher("default");
			dispatcher.forward(request, response);
		}
	}
}
