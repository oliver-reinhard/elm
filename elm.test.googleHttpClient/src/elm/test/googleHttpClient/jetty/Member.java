package elm.test.googleHttpClient.jetty;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

public class Member implements AsyncListener {
	
	private final JettyServlet Member;
	final String _name;
	final AtomicReference<AsyncContext> _async = new AtomicReference<>();

	Member(JettyServlet jettyServlet, String name) {
		Member = jettyServlet;
		_name = name;
	}

	@Override
	public void onTimeout(AsyncEvent event) throws IOException {
		JettyServlet.LOG.debug("resume request");
		AsyncContext async = _async.get();
		if (async != null && _async.compareAndSet(async, null)) {
			HttpServletResponse response = (HttpServletResponse) async.getResponse();
			response.setContentType("text/json;charset=utf-8");
			response.getOutputStream().write("{action:\"poll\"}".getBytes());
			async.complete();
		}
	}

	@Override
	public void onStartAsync(AsyncEvent event) throws IOException {
		event.getAsyncContext().addListener(this);
	}

	@Override
	public void onError(AsyncEvent event) throws IOException {
	}

	@Override
	public void onComplete(AsyncEvent event) throws IOException {
	}
}