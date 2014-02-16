package elm.hs.api.sim.server;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.hs.api.model.HomeServerResponse;

@SuppressWarnings("serial")
public abstract class AbstractHomeServerServlet extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			HomeServerResponse homeServerResponse = getHomeServerResponse(request);
			sendSingleMessage(response, homeServerResponse);
		} catch (RuntimeException e) {
			e.printStackTrace();
			response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500, "Server error: " + e.getMessage());
		}
	}

	/**
	 * @return  {@code null} if request could not be satisfied; this causes an error {@code 400} to be returned to the caller
	 */
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

	/** Parses a request body of {@code "data=<nnn>"} where {@code nnn} is a number of type short.
	 * 
	 * @return {@code null} request contains no body <em>and</em> {@code optional == true}
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	protected Short extractShort(HttpServletRequest request, String id, boolean optional, Logger log) throws IllegalArgumentException, IOException {
		byte[] buf = new byte[10];
		try {
			ServletInputStream stream = request.getInputStream();
			int len = stream.read(buf);
			if (len > 0) {
				String data = new String(buf, 0, len);
				if (data.startsWith("data=")) {
					String temperatureStr = data.substring(5);
					return Short.parseShort(temperatureStr);
				}
			} else if (optional) {
				return null;
			}
		} catch (NumberFormatException | IOException e) {
			log.log(Level.SEVERE, "Unexpected request data: \"" + buf + "\"", e);
			throw e;
		}
		log.log(Level.SEVERE, "Mandatory \"data\" content missing.");
		throw new IllegalArgumentException();
	}
}
