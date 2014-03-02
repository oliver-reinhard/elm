package elm.hs.api.client;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;

import elm.hs.api.HomeServerService;
import elm.hs.api.model.HomeServerResponse;
import elm.scheduler.model.RemoteDeviceUpdateClient;
import elm.util.ClientException;

public class HomeServerInternalApiClient extends AbstractHomeServerClient implements RemoteDeviceUpdateClient {

	protected final HomeServerPublicApiClient publicClient;

	public HomeServerInternalApiClient(String pass, HomeServerPublicApiClient publicClient) throws URISyntaxException {
		this(HomeServerService.ADMIN_USER, pass, publicClient);
	}

	public HomeServerInternalApiClient(String user, String pass, HomeServerPublicApiClient publicClient) throws URISyntaxException {
		this(new URI(publicClient.getBaseUri().getScheme(), null, publicClient.getBaseUri().getHost(), HomeServerService.INTERNAL_API_PORT, null, null, null),
				user, pass, publicClient);
	}

	/**
	 * @param baseUri
	 *            the server URI including an optional port argument, but without any resource path elements, cannot be {@code null}
	 * @param user
	 *            cannot be {@code null}
	 * @param pass
	 *            cannot be {@code null}
	 * @param publicClient
	 *            cannot be {@code null} and is assumed to be started when the first invocation of {@link HomeServerInternalApiClient} method is made.
	 * @throws URISyntaxException
	 */
	public HomeServerInternalApiClient(URI uri, String user, String pass, HomeServerPublicApiClient publicClient) throws URISyntaxException {
		super(uri, user, pass);
		assert publicClient != null;
		this.publicClient = publicClient;
	}

	/**
	 * 
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @param newTemp
	 *            in 1/10°C, cannot be {@code < 0}
	 * @return the new scald temperature in in 1/10 degree Celsius, or {@code null}; never {@code null} (the value is a {@link Short} for mocking purposes)
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public Short setScaldProtectionTemperature(String deviceID, int newTemp) throws ClientException {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		publicClient.setReferenceTemperature(deviceID, newTemp);
		// Set reference-temperature protection flag => no longer user-changeable
		doPost("/cmd/VF/" + deviceID, "data=1", new int[] { HttpStatus.OK_200 });
		// scald protection value is in FULL DEGREES Celcius!
		ContentResponse response = doPost("/cmd/Vv/" + deviceID, "data=" + (newTemp / 10), new int[] { HttpStatus.OK_200 });
		if (response != null) {
			final HomeServerResponse result = getGson().fromJson(response.getContentAsString(), HomeServerResponse.class);
			if (result.response == null || result.response.data == null) {
				log.severe("Setting scald temperature failed: no result returned");
				throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR);
			}
			final String confirmedTemp = result.response.data;
			short value = Short.parseShort(confirmedTemp);
			log.info("New scald temperature = " + value);
			return value;
		}
		throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR);
	}

	/**
	 * Removes the reference-temperature protection and resets the previous reference temperature if {@code previouseTemp} is not {@code null}
	 * 
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @param previousTemp
	 *            in 1/10 degree Celsius, cannot be {@code < 0}, but can be {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public void clearScaldProtection(String deviceID, Integer previousTemp) throws ClientException {
		assert previousTemp == null || previousTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		doPost("/cmd/VF/" + deviceID, "data=0", new int[] { HttpStatus.OK_200 });
		if (previousTemp != null) {
			publicClient.setReferenceTemperature(deviceID, previousTemp);
		}
	}
}
