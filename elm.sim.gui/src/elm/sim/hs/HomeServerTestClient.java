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

import elm.sim.hs.model.Device;
import elm.sim.hs.model.HomeServerObject;
import elm.sim.hs.model.HomeServerResponse;

public class HomeServerTestClient {

	private static final String DEFAULT_HOME_SERVER_URI = "http://192.168.204.204";

	public static final int HTTP_OK = 200;
	public static final int HTTP_ACCEPTED = 202;

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

	public HomeServerResponse getServerStatus() {
		return doGet("", HomeServerResponse.class);
	}

	/**
	 * Returns those devices not registered at this Home Server, regardless of whether they are currently turned on or off.
	 * 
	 * @return {@code null} on errors
	 */
	public HomeServerResponse getRegisteredDevices() {
		return doGet("/devices", HomeServerResponse.class);
	}

	/**
	 * Returns all devices that the Home Server ever contacted since its last reboot. This includes devices that are not registered at this Home Server.
	 * <p>
	 * <b>Note: </b>this method does not need to be called. It would be used to (manually) register new devices at the Home Server.
	 * </p>
	 * <p>
	 * 
	 * @return {@code null} on errors
	 */
	public HomeServerResponse getAllDevices() {
		return doGet("/devices?showCache=true", HomeServerResponse.class);
	}

	public HomeServerResponse getDeviceStatus(String deviceID) {
		assert deviceID != null && !deviceID.isEmpty();
		return doGet("/devices/status/" + deviceID, HomeServerResponse.class);
	}

	/**
	 * Initializes a device discovery an an update of the its device list at the Home Server.
	 * <p>
	 * <b>Note: </b>this method does not need to be called. Discovery is used before new devices can be registered at the Home Server.
	 * </p>
	 * <p>
	 * <b>Note 2: </b>the discovery process, i.e. the invocation of this method, blocks out other calls for up to 10 seconds; the devices list may be incomplete
	 * before that period has expired.
	 * </p>
	 * 
	 * @return {@code false} if the operation ended in a status {@code != 200} or if it threw an exception, else {@code true}
	 */
	public boolean discoverDevices() {
		return doPost("/devices", "autoConnect=false", new int[] { HTTP_ACCEPTED });
	}

	public Short getDemandTemperature(String deviceID) {
		assert deviceID != null && !deviceID.isEmpty();
		HomeServerResponse result = doGet("/devices/setpoint/" + deviceID, HomeServerResponse.class);
		if (result != null) {
			return result.devices.get(0).status.setpoint;
		} else {
			return null;
		}
	}

	/**
	 * 
	 * @param newTemp
	 *            in 1/10 degree Celsius, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return {@code false} if the operation ended in a status {@code != 200} or if it threw an exception, else {@code true}
	 */
	public boolean setDemandTemperature(String deviceID, int newTemp) {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		return doPost("/devices/setpoint/" + deviceID, "data=" + newTemp, new int[] { HTTP_OK });
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
	private <T extends HomeServerObject> T doGet(String resourcePath, Class<T> resultClass) {
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
	 * @return {@code false} if the post ended in a status {@code != 200} or if it threw an exception, else {@code true}
	 */
	private boolean doPost(String resourcePath, String content, int[] httpSuccessStatuses) {
		assert resourcePath != null && !resourcePath.isEmpty();
		try {
			Request postRequest = client.newRequest(getBaseUri() + resourcePath).method(HttpMethod.POST);
			if (content != null) {
				postRequest.content(new StringContentProvider(content), "application/x-www-form-urlencoded");
			}
			Response response;
			response = postRequest.send();
			int status = response.getStatus();

			final String desc = "POST " + resourcePath + " (" + content + ") Response";
			if (!isSuccess(httpSuccessStatuses, status)) {
				LOG.log(Level.SEVERE, "Posting resource path failed: " + resourcePath + ", Status: " + status);
				if (LOG.getLevel() == Level.INFO) {
					System.out.println(desc + " status    = " + status);
				}
				return false;
			} else if (LOG.getLevel() == Level.INFO) {
				System.out.println();
				System.out.println(desc + " status    = " + status);
			}
			return true;
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			LOG.log(Level.SEVERE, "Posting resource path failed: " + resourcePath, e);
			return false;
		}
	}

	private boolean isSuccess(int[] httpSuccessStatuses, int status) {
		for (int s : httpSuccessStatuses) {
			if (status == s) return true;
		}
		return false;
	}

	public static void main(String[] args) throws URISyntaxException {

		final String defaultPass = "geheim";
		HomeServerTestClient client = new HomeServerTestClient(DEFAULT_HOME_SERVER_URI, "admin", defaultPass);

		try {
			client.start();

			// ContentResponse response = client.GET("http://localhost:8080/hs?action");
			client.getServerStatus();

			HomeServerResponse response = client.getRegisteredDevices();
			if (response != null) {
				for (Device dev : response.devices) {
					if (!dev.isAlive()) {
						LOG.warning("Registered Device " + dev.id + " is not connected.");
						// Do not send request with this device ID.
					}
				}
			}

			String deviceID = "A001FFFF8A";
			if (response.isDeviceAlive(deviceID)) {
				client.getDeviceStatus(deviceID);
	
				// Change demand temperature:
				client.setDemandTemperature(deviceID, 190);
	
				Short demandTemp = client.getDemandTemperature(deviceID);
				System.out.println("Demand temp = " + demandTemp);
			}

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
