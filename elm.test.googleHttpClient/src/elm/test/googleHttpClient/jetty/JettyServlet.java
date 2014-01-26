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

		Member member = new Member();
		member.setName("member no. " + (counter++));
		sendSingleMessage(response, member);
	}

	private void sendSingleMessage(HttpServletResponse response, Member member) throws IOException {
		response.setContentType("text/json;charset=utf-8");
		Gson gson = new GsonBuilder().create(); // new GsonBuilder().setPrettyPrinting().create();
		String str = gson.toJson(member);
		LOG.info("Sending Member: " + str);
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