package elm.hs.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public final class ClientUtil {

	private ClientUtil() {
		// prevent instantiation
	}

	protected static void initSslContextFactory(HttpClient client) {
		SslContextFactory factory = client.getSslContextFactory();
		if (factory != null) {
			// Enable access to non-trusted SSL servers.
			factory.setTrustAll(true);
		}
	}
}
