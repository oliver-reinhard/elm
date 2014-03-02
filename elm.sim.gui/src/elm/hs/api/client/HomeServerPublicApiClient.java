package elm.hs.api.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.jetty.http.HttpStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.hs.api.HomeServerService;
import elm.hs.api.model.ElmUserFeedback;
import elm.hs.api.model.HomeServerResponse;
import elm.hs.api.model.Service;
import elm.scheduler.ElmUserFeedbackClient;
import elm.util.ClientException;

public class HomeServerPublicApiClient extends AbstractHomeServerClient implements ElmUserFeedbackClient {

	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Use the default server URI and administration user.
	 * 
	 * @param pass
	 *            cannot be {@code null} or empty
	 * @throws URISyntaxException
	 */
	public HomeServerPublicApiClient(String pass) throws URISyntaxException {
		super(HomeServerService.DEFAULT_URI, pass);
	}

	/**
	 * @param baseUri
	 *            the server URI including an optional port argument, but without any resource path elements, cannot be {@code null}
	 * @param pass
	 *            cannot be {@code null} or empty
	 */
	public HomeServerPublicApiClient(URI baseUri, String pass) {
		super(baseUri, pass);
	}

	/**
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
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public HomeServerResponse getServerStatus() throws ClientException {
		return doGet("", HomeServerResponse.class);
	}

	/**
	 * Returns all devices registered at this Home Server, regardless of whether they are currently turned on or off.
	 * 
	 * @return never {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public HomeServerResponse getRegisteredDevices() throws ClientException {
		return doGet("/devices", HomeServerResponse.class);
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
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 202} or if the execution threw an exception
	 */
	public void discoverDevices() throws ClientException {
		doPost("/devices", "autoConnect=false", new int[] { HttpStatus.ACCEPTED_202 });
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
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public HomeServerResponse getDeviceStatus(String deviceID) throws ClientException {
		assert deviceID != null && !deviceID.isEmpty();
		return doGet("/devices/status/" + deviceID, HomeServerResponse.class);
	}

	/**
	 * Returns the current reference temperature for the given device.
	 * 
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return temperature in [1/10°C]
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public short getReferenceTemperature(String deviceID) throws ClientException {
		assert deviceID != null && !deviceID.isEmpty();
		HomeServerResponse result = doGet("/devices/setpoint/" + deviceID, HomeServerResponse.class);
		return result.devices.get(0).status.setpoint;
	}

	/**
	 * Sets the reference temperature (a.k.a <em>setpoint</em)> for the given device.
	 * 
	 * @param newTemp
	 *            in [1/10°C], cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public void setReferenceTemperature(String deviceID, int newTemp) throws ClientException {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		doPost("/devices/setpoint/" + deviceID, "data=" + newTemp, new int[] { HttpStatus.OK_200 });
	}

	// ------ Services offered by Sim Home Servers ------

	@Override
	public boolean supportsUserFeedback() throws ClientException {
		HomeServerResponse statusResponse = getServerStatus();
		for (Service service : statusResponse.services) {
			if (service.elmFeedback != null) {
				return true;
			}
		}
		return false;
	}
	@Override
	public HomeServerResponse getFeedbackDevices() throws ClientException {
		return doGet("/devices/feedback", HomeServerResponse.class);
	}

	@Override
	public void updateUserFeedback(List<ElmUserFeedback> feedback) throws ClientException {
		assert feedback != null;

		doPost("/devices/feedback", gson.toJson(feedback), new int[] { HttpStatus.OK_200 });
	}
}
