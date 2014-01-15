package elm.test.jetty;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
 
public class HelloHandler extends AbstractHandler {
 
    final String _greeting;
 
    final String _body;
 
    public HelloHandler() {
        this("Hello World");
    }
 
    public HelloHandler(String greeting) {
        this(greeting, null);
    }
 
    public HelloHandler(String greeting, String body) {
        _greeting = greeting;
        _body = body;
    }
 
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        response.getWriter().println("<h1>" + _greeting + "</h1>");
        response.getWriter().println(_body != null ?_body : target);
    }
}
