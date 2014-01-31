package elm.hs.client;

import java.net.URISyntaxException;

import org.eclipse.jetty.http.HttpStatus;

import elm.hs.model.HomeServerResponse;

public class HomeServerPublicApiClient extends AbstractHomeServerClient {

	/** The default URI according to API v1.0 documentation. */
	public static final String DEFAULT_HOME_SERVER_URI = "http://192.168.204.204";

	/**
	 * Use the default server URI and administration user.
	 * 
	 * @param pass
	 *            cannot be {@code null}
	 * @throws URISyntaxException
	 */
	public HomeServerPublicApiClient(String pass) throws URISyntaxException {
		super(DEFAULT_HOME_SERVER_URI, pass);
	}

	/**
	 * 
	 * @param baseUri
	 *            the server URI including an optional port argument, but without any resource path elements
	 * @param user
	 *            cannot be {@code null}
	 * @param pass
	 *            cannot be {@code null}
	 * @throws URISyntaxException
	 */
	public HomeServerPublicApiClient(String baseUri, String user, String pass) throws URISyntaxException {
		super(baseUri, user, pass);
	}

	public HomeServerResponse getServerStatus() {
		return doGet("", HomeServerResponse.class);
	}

	/**
	 * Returns all devices registered at this Home Server, regardless of whether they are currently turned on or off.
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
		return doPost("/devices", "autoConnect=false", new int[] { HttpStatus.ACCEPTED_202 }) != null;
	}

	public Short getReferenceTemperature(String deviceID) {
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
	public boolean setReferenceTemperature(String deviceID, int newTemp) {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		return doPost("/devices/setpoint/" + deviceID, "data=" + newTemp, new int[] { HttpStatus.OK_200 }) != null;
	}
}
