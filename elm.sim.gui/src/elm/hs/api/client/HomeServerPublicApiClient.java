package elm.hs.api.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.jetty.http.HttpStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.hs.api.ElmUserFeedback;
import elm.hs.api.ElmUserFeedbackService;
import elm.hs.api.HomeServerResponse;
import elm.hs.api.HomeServerService;
import elm.hs.api.Service;
import elm.util.ClientException;

public class HomeServerPublicApiClient extends AbstractHomeServerClient implements HomeServerService, ElmUserFeedbackService {

	/** FIXME As of 2014-03-05 certain POST and PUT operations return an error 500 while still processing the request OK. */
	public static final int ERROR_500_FIX = HttpStatus.INTERNAL_SERVER_ERROR_500;

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

	@Override
	public HomeServerResponse getServerStatus() throws ClientException {
		return doGet("", HomeServerResponse.class);
	}

	@Override
	public void discoverDevices() throws ClientException {
		doPost("/devices", "autoConnect=false", new int[] { HttpStatus.ACCEPTED_202 });
	}

	@Override
	public HomeServerResponse getRegisteredDevices() throws ClientException {
		return doGet("/devices", HomeServerResponse.class);
	}
	
	@Override
	public HomeServerResponse getAllDevices() throws ClientException {
		return doGet("/devices?showCache=true", HomeServerResponse.class);
	}
	
	@Override
	public HomeServerResponse getDeviceStatus(String deviceID) throws ClientException {
		assert deviceID != null && !deviceID.isEmpty();
		return doGet("/devices/status/" + deviceID, HomeServerResponse.class);
	}

	@Override
	public void manageDevice(String deviceID) throws ClientException {
		assert deviceID != null && !deviceID.isEmpty();
		doPut("/devices/" + deviceID, "forcedConnect=true", new int[] { HttpStatus.OK_200 });
	}

	@Override
	public void unmanageDevice(String deviceID) throws ClientException {
		assert deviceID != null && !deviceID.isEmpty();
		doDelete("/devices/" + deviceID, "", new int[] { HttpStatus.OK_200 });
	}

	@Override
	public short getReferenceTemperature(String deviceID) throws ClientException {
		assert deviceID != null && !deviceID.isEmpty();
		HomeServerResponse result = doGet("/devices/setpoint/" + deviceID, HomeServerResponse.class);
		return result.devices.get(0).status.setpoint;
	}

	@Override
	public void setReferenceTemperature(String deviceID, int newTemp) throws ClientException {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		doPost("/devices/setpoint/" + deviceID, "data=" + newTemp, new int[] { HttpStatus.OK_200, ERROR_500_FIX });
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
		doPost("/devices/feedback", gson.toJson(feedback, ElmUserFeedback.ELM_USER_FEEDBACK_LIST_TYPE), new int[] { HttpStatus.OK_200 });
	}
}
