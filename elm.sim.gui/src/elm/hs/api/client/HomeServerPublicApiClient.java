package elm.hs.api.client;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jetty.http.HttpStatus;

import elm.hs.api.model.HomeServerResponse;
import elm.util.ClientException;

public class HomeServerPublicApiClient extends AbstractHomeServerClient {

	/** The default URI according to API v1.0 documentation. */
	public static final URI DEFAULT_HOME_SERVER_URI = URI.create("http://192.168.204.204");

	/**
	 * Use the default server URI and administration user.
	 * 
	 * @param pass
	 *            cannot be {@code null} or empty
	 * @throws URISyntaxException
	 */
	public HomeServerPublicApiClient(String pass) throws URISyntaxException {
		super(DEFAULT_HOME_SERVER_URI, pass);
	}

	/**
	 * 
	 * @param baseUri
	 *            the server URI including an optional port argument, but without any resource path elements, cannot be {@code null}
	 * @param pass
	 *            cannot be {@code null} or empty
	 */
	public HomeServerPublicApiClient(URI baseUri, String pass) {
		super(baseUri, pass);
	}
	/**
	 * 
	 * @param baseUri
	 *            the server URI including an optional port argument, but without any resource path elements, cannot be {@code null}
	 * @param user
	 *            cannot be {@code null} or empty
	 * @param pass
	 *            cannot be {@code null} or empty
	 */
	public HomeServerPublicApiClient(URI baseUri, String user, String pass) {
		super(baseUri, user, pass);
	}

	/**
	 * @return never {@code null}
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public HomeServerResponse getServerStatus() throws ClientException {
		return doGet("", HomeServerResponse.class);
	}

	/**
	 * Returns all devices registered at this Home Server, regardless of whether they are currently turned on or off.
	 * 
	 * @return never {@code null}
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public HomeServerResponse getRegisteredDevices() throws ClientException {
		return doGet("/devices", HomeServerResponse.class);
	}

	/**
	 * Returns all devices that the Home Server ever contacted since its last reboot. This includes devices that are not registered at this Home Server.
	 * <p>
	 * <b>Note: </b>this method does not need to be called. It would be used to (manually) register new devices at the Home Server.
	 * </p>
	 * <p>
	 * 
	 * @return never {@code null}
	 * @throws ClientException 
	 */
	public HomeServerResponse getAllDevices() throws ClientException {
		return doGet("/devices?showCache=true", HomeServerResponse.class);
	}

	/**
	 * 
	 * @param deviceID
	 * @return never {@code null}
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public HomeServerResponse getDeviceStatus(String deviceID) throws ClientException {
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
	 * @throws ClientException if the operation ended in a status {@code != 202} or if the execution threw an exception
	 */
	public void discoverDevices() throws ClientException {
		 doPost("/devices", "autoConnect=false", new int[] { HttpStatus.ACCEPTED_202 });
	}

	public short getReferenceTemperature(String deviceID) throws ClientException {
		assert deviceID != null && !deviceID.isEmpty();
		HomeServerResponse result = doGet("/devices/setpoint/" + deviceID, HomeServerResponse.class);
			return result.devices.get(0).status.setpoint;
	}

	/**
	 * 
	 * @param newTemp
	 *            in 1/10 degree Celsius, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public void setReferenceTemperature(String deviceID, int newTemp) throws ClientException {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		doPost("/devices/setpoint/" + deviceID, "data=" + newTemp, new int[] { HttpStatus.OK_200 });
	}
}
