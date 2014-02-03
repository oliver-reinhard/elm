package elm.sim.hs.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.hs.api.model.HomeServerResponse;

@SuppressWarnings("serial")
public abstract class AbstractHomeServerServlet extends HttpServlet {

	protected final Logger LOG = Log.getLogger(AbstractHomeServerServlet.class);

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		sendSingleMessage(response, getHomeServerResponse(request));
	}

	protected abstract HomeServerResponse getHomeServerResponse(HttpServletRequest request);

	protected void sendSingleMessage(HttpServletResponse response, HomeServerResponse data) throws IOException {
		if (data == null) {
			response.sendError(HttpStatus.BAD_REQUEST_400, "No result");
		} else {
			response.setContentType("text/json;charset=utf-8");
			response.setStatus(HttpStatus.OK_200);
			Gson gson = new GsonBuilder().create(); // new GsonBuilder().setPrettyPrinting().create();
			String str = gson.toJson(data);
			response.getWriter().println(str);
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}
}
