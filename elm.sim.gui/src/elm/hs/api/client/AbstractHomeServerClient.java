package elm.hs.api.client;

import java.net.URI;

import elm.util.AbstractJSONClient;

public abstract class AbstractHomeServerClient extends AbstractJSONClient {
	
	/** The default URI according to API v1.0 documentation. */
	public static final URI DEFAULT_HOME_SERVER_URI = URI.create("http://192.168.204.204");

	/** The Home Server administration user according to API v1.0 documentation. */
	public static final String HOME_SERVER_ADMIN_USER = "admin";
	
	/** The Home Server administration user password according to API v1.0 documentation. */
	public static final String HOME_SERVER_DEFAULT_PASSWORD = "geheim";

	public AbstractHomeServerClient(URI baseUri, String pass) {
		this(baseUri, HOME_SERVER_ADMIN_USER, pass);
	}

	public AbstractHomeServerClient(URI baseUri, String user, String pass) {
		super(baseUri, user, pass);
	}
}