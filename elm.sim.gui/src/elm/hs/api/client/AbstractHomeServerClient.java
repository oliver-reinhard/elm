package elm.hs.api.client;

import java.net.URI;

import elm.hs.api.HomeServerService;
import elm.util.AbstractJSONClient;

public abstract class AbstractHomeServerClient extends AbstractJSONClient {
	
	public AbstractHomeServerClient(URI baseUri, String pass) {
		this(baseUri, HomeServerService.ADMIN_USER, pass);
	}

	public AbstractHomeServerClient(URI baseUri, String user, String pass) {
		super(baseUri, user, pass);
	}
}