package elm.sim.hs;

import java.net.URI;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.StringUtil;

public class AuthenticatingHttpClient extends HttpClient {
	
	private final String basicAuthentication;
	
	public AuthenticatingHttpClient(String user, String pass) {
		// Code from BasicAuthentication
		 basicAuthentication = "Basic " + B64Code.encode(user + ":" + pass, StringUtil.__ISO_8859_1);
	}

	@Override
	public Request newRequest(URI uri) {
		Request result = super.newRequest(uri);
		result.header(HttpHeader.AUTHORIZATION, basicAuthentication);
		return result;
	}
}
