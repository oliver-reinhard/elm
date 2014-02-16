package elm.util;

import java.net.URI;
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
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class AbstractJSONClient {


	protected final Logger log = Logger.getLogger(getClass().getName());

	private final URI baseUri;
	private final HttpClient client;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();


	public AbstractJSONClient(URI baseUri, String user, String pass) {
		assert baseUri != null;
		assert user != null && !user.isEmpty();
		assert pass != null && !pass.isEmpty();

		this.baseUri = baseUri;
		boolean https = baseUri.getScheme().toLowerCase().startsWith("https");
		SslContextFactory sslContextFactory = https ? new SslContextFactory() : null;
		//
		// BUG (jetty 9.0.6): the HttpClient simply FORGETS to put the authentication header into the request.
		//
		// Replace AuthenticationHttpClient by HttpClient once the bug has been fixed and the header is correct
		//
		client = new AuthenticatingHttpClient(user, pass, sslContextFactory);

		// The following lines are without effect due to the above-mentioned BUG:
		String realm = "ELM-Realm";
		AuthenticationStore authStore = client.getAuthenticationStore();
		authStore.addAuthentication(new BasicAuthentication(baseUri, realm, user, pass));
		
		log.setLevel(Level.SEVERE);
	}

	public URI getBaseUri() {
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

	public void setLogLevel(Level level) {
		log.setLevel(level);
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
	protected <T> T doGet(String resourcePath, Class<T> resultClass) throws ClientException {
		return doGet(resourcePath, resultClass, new int[] { HttpStatus.OK_200 });
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
	protected <T> T doGet(String resourcePath, Class<T> resultClass, int[] httpSuccessStatuses) throws ClientException {
		assert resourcePath != null;
		assert resultClass != null;
		ClientException exception;

		try {
			ContentResponse response = client.GET(getBaseUri() + resourcePath);
			final String responseAsString = response.getContentAsString();
			int status = response.getStatus();
			if (!isSuccess(httpSuccessStatuses, status)) {
				log.log(Level.SEVERE, "Querying resource path failed: " + (resourcePath.isEmpty() ? "\"\"" : resourcePath) + ", Status: " + status);
				if (log.isLoggable(Level.INFO)) {
					final String desc = "GET " + resourcePath + " Response";
					System.out.println(desc + " status    = " + status);
					System.out.println(desc + " as String = " + responseAsString);
				}
				throw new ClientException(ClientException.Error.APPLICATION_FAILURE_RESPONSE);
			}

			final T result = getGson().fromJson(responseAsString, resultClass);

			if (log.isLoggable(Level.INFO)) {
				final String desc = "GET " + resourcePath;
				System.out.println();
				System.out.println(desc + " Response status    = " + response.getStatus());
				System.out.println(desc + " Response as String = " + responseAsString);
				System.out.println(desc + " Result             = " + result.getClass().getName() + ": " + getGson().toJson(result));
			}
			return result;

		} catch (InterruptedException e) {
			exception = new ClientException(e);
		} catch (ExecutionException e) {
			exception = new ClientException(e);
		} catch (TimeoutException e) {
			exception = new ClientException(e);
		}
		log.log(Level.SEVERE, "Querying resource path failed: " + resourcePath, exception);
		throw exception;
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
	 * @return {@code null} if the post ended in a non-success status or if it threw an exception, else the response object
	 */
	protected ContentResponse doPost(String resourcePath, String content, int[] httpSuccessStatuses) throws ClientException {
		assert resourcePath != null && !resourcePath.isEmpty();
		ClientException exception;

		try {
			Request postRequest = client.newRequest(getBaseUri() + resourcePath).method(HttpMethod.POST);
			if (content != null) {
				postRequest.content(new StringContentProvider(content), "application/x-www-form-urlencoded");
			}
			ContentResponse response = postRequest.send();
			int status = response.getStatus();

			final String desc = log.isLoggable(Level.INFO) ? "POST " + resourcePath + " (" + content + ") Response" : null;
			if (!isSuccess(httpSuccessStatuses, status)) {
				log.log(Level.SEVERE, "Posting resource path failed: " + resourcePath + ", Status: " + status);
				if (log.isLoggable(Level.INFO)) {
					System.out.println(desc + " status    = " + status);
				}
				return null;
			} else if (log.isLoggable(Level.INFO)) {
				System.out.println();
				System.out.println(desc + " status    = " + status);
			}
			return response;

		} catch (InterruptedException e) {
			exception = new ClientException(e);
		} catch (ExecutionException e) {
			exception = new ClientException(e);
		} catch (TimeoutException e) {
			exception = new ClientException(e);
		}
		log.log(Level.SEVERE, "Posting resource path failed: " + resourcePath, exception);
		throw exception;
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