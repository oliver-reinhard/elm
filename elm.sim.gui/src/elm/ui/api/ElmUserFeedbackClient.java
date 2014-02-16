package elm.ui.api;

import java.net.URI;

import org.eclipse.jetty.http.HttpStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.util.AbstractJSONClient;
import elm.util.ClientException;

public class ElmUserFeedbackClient extends AbstractJSONClient {

	/** The Feedback Server administration user according to API v1.0 documentation. */
	public static final String HOME_SERVER_ADMIN_USER = "admin";
	
	/** The Feedback Server administration user password according to API v1.0 documentation. */
	public static final String HOME_SERVER_DEFAULT_PASSWORD = "geheim";
	
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * @param baseUri
	 *            the server URI including an optional port argument, but without any resource path elements, cannot be {@code null}
	 * @param pass
	 *            cannot be {@code null} or empty
	 */
	public ElmUserFeedbackClient(URI baseUri, String pass) {
		this(baseUri, HOME_SERVER_ADMIN_USER, pass);
	}

	/**
	 * @param baseUri
	 *            the server URI including an optional port argument, but without any resource path elements, cannot be {@code null}
	 * @param user
	 *            cannot be {@code null} or empty
	 * @param pass
	 *            cannot be {@code null} or empty
	 */
	public ElmUserFeedbackClient(URI baseUri, String user, String pass) {
		super(baseUri, user, pass);
	}

	/**
	 * Sends device user feedback to UI of the respective device.
	 * 
	 * @param feedback
	 *            cannot be {@code null} or empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public void updateUserFeedback(ElmUserFeedback feedback) throws ClientException {
		assert feedback != null;

		doPost("/devices/feedback", gson.toJson(feedback), new int[] { HttpStatus.OK_200 });
		
	}
}
