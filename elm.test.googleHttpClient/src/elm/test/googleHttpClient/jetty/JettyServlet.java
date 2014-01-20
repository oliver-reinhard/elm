package elm.test.googleHttpClient.jetty;

import java.io.IOException;

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

@SuppressWarnings("serial")
public class JettyServlet extends HttpServlet {
	static final Logger LOG = Log.getLogger(JettyServlet.class);

	private int counter = 1;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		Member2 member = new Member2();
		member.setName("oliver " + (counter++));
		sendSingleMessage(response, member);
	}

	private void sendSingleMessage(HttpServletResponse response, Member2 member) throws IOException {
		response.setContentType("text/json;charset=utf-8");
//		response.setStatus(HttpServletResponse.SC_OK);
//		StringBuilder buf = new StringBuilder();
//		buf.append("{\"name\":\"");
//		buf.append(member.getName());
//		buf.append("\"}");
//		LOG.info("Sending Member: " + buf.toString());
//		response.getWriter().println(buf.toString());
		// byte[] bytes = buf.toString().getBytes("utf-8");
		// response.setContentLength(bytes.length);
		// response.getOutputStream().write(bytes);

//		 Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Gson gson = new GsonBuilder().create();
		String str = gson.toJson(member);
		LOG.info("Sending Member: " + str);
////		byte[] bytes = str.getBytes("utf-8");
////		response.setContentLength(bytes.length);
////		response.getOutputStream().write(bytes);
		response.getWriter().println(str);
		
		//response.flushBuffer();
	}

	// Serve the HTML with embedded CSS and Javascript. This should be static content and should use real JS libraries.
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
