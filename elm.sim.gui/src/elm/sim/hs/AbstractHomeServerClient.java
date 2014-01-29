package elm.sim.hs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.sim.hs.model.HomeServerObject;

public abstract class AbstractHomeServerClient {

	public static final int HTTP_OK = 200;
	public static final int HTTP_ACCEPTED = 202;

	/** The Home Server administration user according to API v1.0 documentation. */
	public static final String HOME_SERVER_ADMIN_USER = "admin";
	/** The Home Server administration user password according to API v1.0 documentation. */
	public static final String HOME_SERVER_DEFAULT_PASSWORD = "geheim";

	protected final Logger LOG = Logger.getLogger(getClass().getName());

	private final String baseUri;
	private final HttpClient client;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public AbstractHomeServerClient(String baseUri, String pass) throws URISyntaxException {
		this(baseUri, HOME_SERVER_ADMIN_USER, pass);
	}

	public AbstractHomeServerClient(String baseUri, String user, String pass) throws URISyntaxException {
		assert baseUri != null && ! baseUri.isEmpty();
		this.baseUri = baseUri;
		boolean https = baseUri.toLowerCase().startsWith("https");
		SslContextFactory sslContextFactory = https ? new SslContextFactory() : null;
		//
		// BUG (jetty 9.0.6): the HttpClient simply FORGETS to put the authentication header into the request.
		//
		// Replace AuthenticationHttpClient by HttpClient once the bug has been fixed and the header is correct
		//
		client = new AuthenticatingHttpClient(user, pass, sslContextFactory);

		// The following lines are without effect due to the above-mentioned BUG:
		URI uri = new URI(baseUri);
		String realm = "MyRealm";
		AuthenticationStore authStore = client.getAuthenticationStore();
		authStore.addAuthentication(new BasicAuthentication(uri, realm, user, pass));
	}

	public String getBaseUri() {
		return baseUri;
	}

	public HttpClient getClient() {
		return client;
	}

	public void start() throws Exception {
		client.start();
	}

	public void stop() throws Exception {
		client.stop();
	}

	public Gson getGson() {
		return gson;
	}

	/**
	 * Sends a GET request, processes the return status, handles exceptions. The request is considered successful if it yields {@value #HTTP_OK}.
	 * 
	 * @param resourcePath
	 *            cannot be {@code null} but can be empty
	 * @param resultClass
	 *            cannot be {@code null}
	 * @return {@code null} if not-OK return status or an exception
	 */
	protected <T extends HomeServerObject> T doGet(String resourcePath, Class<T> resultClass) {
		return doGet(resourcePath, resultClass, new int[] { HTTP_OK });
	}

	/**
	 * Sends a GET request, processes the return status, handles exceptions.
	 * 
	 * @param resourcePath
	 *            cannot be {@code null} but can be empty
	 * @param resultClass
	 *            cannot be {@code null}
	 * @param httpSuccessStatuses
	 *            the list of HTTP statuses that are to be considered a success
	 * @return {@code null} if not-OK return status or an exception
	 */
	private <T extends HomeServerObject> T doGet(String resourcePath, Class<T> resultClass, int[] httpSuccessStatuses) {
		assert resourcePath != null;
		assert resultClass != null;

		try {
			ContentResponse response = client.GET(getBaseUri() + resourcePath);
			final String responseAsString = response.getContentAsString();
			int status = response.getStatus();
			if (!isSuccess(httpSuccessStatuses, status)) {
				LOG.log(Level.SEVERE, "Querying resource path failed: " + resourcePath + ", Status: " + status);
				if (LOG.getLevel() == Level.INFO) {
					final String desc = "GET " + resourcePath + " Response";
					System.out.println(desc + " status    = " + status);
					System.out.println(desc + " as String = " + responseAsString);
				}
				return null;
			}

			final T result = getGson().fromJson(responseAsString, resultClass);

			if (LOG.getLevel() == Level.INFO) {
				final String desc = "GET " + resourcePath;
				System.out.println();
				System.out.println(desc + " Response status    = " + response.getStatus());
				System.out.println(desc + " Response as String = " + responseAsString);
				System.out.println(desc + " Result             = " + result.getClass().getName() + ": " + getGson().toJson(result));
			}
			return result;

		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			LOG.log(Level.SEVERE, "Querying resource path failed: " + resourcePath, e);
			return null;
		}
	}

	/**
	 * Sends a POST request, processes the return status, handles exceptions.
	 * 
	 * @param resourcePath
	 *            cannot be {@code null} or empty
	 * @param content
	 *            can be {@code null} or empty
	 * @param httpSuccessStatuses
	 *            the list of HTTP statuses that are to be considered a success
	 * @return {@code null} if the post ended in a status {@code != 200} or if it threw an exception, else the response object
	 */
	protected ContentResponse doPost(String resourcePath, String content, int[] httpSuccessStatuses) {
		assert resourcePath != null && !resourcePath.isEmpty();
		try {
			Request postRequest = client.newRequest(getBaseUri() + resourcePath).method(HttpMethod.POST);
			if (content != null) {
				postRequest.content(new StringContentProvider(content), "application/x-www-form-urlencoded");
			}
			ContentResponse response = postRequest.send();
			int status = response.getStatus();

			final String desc = "POST " + resourcePath + " (" + content + ") Response";
			if (!isSuccess(httpSuccessStatuses, status)) {
				LOG.log(Level.SEVERE, "Posting resource path failed: " + resourcePath + ", Status: " + status);
				if (LOG.getLevel() == Level.INFO) {
					System.out.println(desc + " status    = " + status);
				}
				return null;
			} else if (LOG.getLevel() == Level.INFO) {
				System.out.println();
				System.out.println(desc + " status    = " + status);
			}
			return response;
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			LOG.log(Level.SEVERE, "Posting resource path failed: " + resourcePath, e);
			return null;
		}
	}

	/**
	 * @param httpSuccessStatuses
	 *            cannot be {@code null}
	 * @param status
	 * @return {@code true} if the given status is contained in the list of success statuses.
	 */
	protected boolean isSuccess(int[] httpSuccessStatuses, int status) {
		assert httpSuccessStatuses != null;

		for (int s : httpSuccessStatuses) {
			if (status == s) return true;
		}
		return false;
	}

}