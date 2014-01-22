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
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.sim.hs.model.HomeServerObject;
import elm.sim.hs.model.ServerStatus;

public class HomeServerTestClient {

	private static final String DEFAULT_HOME_SERVER_URI = "http://192.168.204.204";

	private static final Logger LOG = Logger.getLogger(HomeServerTestClient.class.getName());

	{
		LOG.setLevel(Level.INFO);
	}

	private final String baseUri;
	private final HttpClient client;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public HomeServerTestClient(String baseUri, String user, String pass) throws URISyntaxException {
		this.baseUri = baseUri;
		//
		// BUG (jetty 9.0.6): the HttpClient simply FORGETS to put the authentication header into the request.
		//
		// Replace AuthenticationHttpClient by HttpClient once the bug has been fixed and the header is correct
		//
		client = new AuthenticatingHttpClient(user, pass);

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

	public ServerStatus getServerStatus() {
		return get("", ServerStatus.class);
	}

	public ServerStatus getDevices(boolean showCache) {
		return get("/devices" + (showCache ? "" : "?showCache=false"), ServerStatus.class);
	}

	public ServerStatus getDeviceStatus(String deviceID) {
		assert deviceID != null && !deviceID.isEmpty();
		return get("/devices/status/" + deviceID, ServerStatus.class);
	}

	/**
	 * Invokes a GET call, processes the return status, handles exceptions.
	 * 
	 * @param resourcePath
	 *            cannot be {@code null} but can be empty
	 * @param resultClass
	 *            cannot be {@code null}
	 * @return {@code null} if not-OK return status or an exception
	 */
	private <T extends HomeServerObject> T get(String resourcePath, Class<T> resultClass) {
		assert resourcePath != null;
		assert resultClass != null;

		try {
			ContentResponse response = client.GET(getBaseUri() + resourcePath);
			final String responseAsString = response.getContentAsString();
			int status = response.getStatus();
			if (status != 200) {
				LOG.log(Level.SEVERE, "Querying resource path failed: " + resourcePath + ", Status " + status);
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
	 * 
	 * @param newTemp
	 *            in 1/10 degree Celsius, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @throws InterruptedException
	 * @throws TimeoutException
	 * @throws ExecutionException
	 */
	public void setDemandTemperature(String deviceID, int newTemp) throws InterruptedException, TimeoutException, ExecutionException {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		final String resourcePath = "/devices/setpoint/" + deviceID;
		final String content = "data=" + newTemp;
		Request postRequest = client.newRequest(getBaseUri() + resourcePath).method(HttpMethod.PUT);
		postRequest.content(new StringContentProvider(content), "application/x-www-form-urlencoded");
		Response response = postRequest.send();
		int status = response.getStatus();

		final String desc = "PUT " + resourcePath + " Response";
		if (status != 200) {
			LOG.log(Level.SEVERE, "Querying resource path failed: " + resourcePath + ", Status " + status);
			if (LOG.getLevel() == Level.INFO) {
				System.out.println(desc + " status    = " + status);
				if (response instanceof ContentResponse) {
					System.out.println(desc + " as String = " + ((ContentResponse) response).getContentAsString());
				}
			}
		} else if (LOG.getLevel() == Level.INFO) {
			System.out.println();
			System.out.println(desc + " status    = " + status);
		}
	}

	public Short getDemandTemperature(String deviceID) {
		assert deviceID != null && !deviceID.isEmpty();
		ServerStatus result = get("/devices/setpoint/" + deviceID, ServerStatus.class);
		if (result != null) {
			return result.devices.get(0).status.setpoint;
		} else {
			return null;
		}
	}

	public static void main(String[] args) throws URISyntaxException {

		final String defaultPass = "geheim";
		HomeServerTestClient client = new HomeServerTestClient(DEFAULT_HOME_SERVER_URI, "admin", defaultPass);

		try {
			client.start();

			// ContentResponse response = client.GET("http://localhost:8080/hs?action");
			client.getServerStatus();

			client.getDevices(true);

			String deviceID = "A001FFFF8A";
			client.getDeviceStatus(deviceID);

			// Change demand temperature:
			client.setDemandTemperature(deviceID, 391);

			Short demandTemp = client.getDemandTemperature(deviceID);
			System.out.println("Demand temp = " + demandTemp);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (client != null) {
					client.stop();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
