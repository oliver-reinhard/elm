package elm.test.jetty;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class HelloServlet extends HttpServlet {

//	@Override
//	public void init(ServletConfig config) throws ServletException {
//		super.init(config);
//	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		ServletOutputStream out = response.getOutputStream();
		out.println("<html>");
		out.println("<h1>Hello World Servlet</h1>");
		out.println("</html>");
		out.flush();
	}
}
