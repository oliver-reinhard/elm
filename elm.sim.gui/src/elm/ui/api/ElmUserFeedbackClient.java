package elm.ui.api;

import java.net.URI;

import elm.hs.api.client.AbstractJSONClient;
import elm.hs.api.client.ClientException;

public class ElmUserFeedbackClient extends AbstractJSONClient {

	/** The Feedback Server administration user according to API v1.0 documentation. */
	public static final String HOME_SERVER_ADMIN_USER = "admin";
	
	/** The Feedback Server administration user password according to API v1.0 documentation. */
	public static final String HOME_SERVER_DEFAULT_PASSWORD = "geheim";

	public ElmUserFeedbackClient(URI baseUri, String pass) {
		this(baseUri, HOME_SERVER_ADMIN_USER, pass);
	}

	public ElmUserFeedbackClient(URI baseUri, String user, String pass) {
		super(baseUri, user, pass);
	}

	public void updateUserFeedback(ElmDeviceUserFeedback feedback) throws ClientException {
		
	}
}
