package elm.hs.api.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;

import elm.hs.api.model.HomeServerResponse;
import elm.util.ClientException;

public class HomeServerInternalApiClient extends AbstractHomeServerClient {

	/** The default port for internal Home Server access according to API v1.0 documentation. */
	private static final int HOME_SERVER_INTERNAL_API_PORT = 8080;

	protected final HomeServerPublicApiClient publicClient;

	public HomeServerInternalApiClient(String pass, HomeServerPublicApiClient publicClient) throws URISyntaxException {
		this(HOME_SERVER_ADMIN_USER, pass, publicClient);
	}

	public HomeServerInternalApiClient(String user, String pass, HomeServerPublicApiClient publicClient) throws URISyntaxException {
		this(publicClient.getBaseUri(), user, pass, publicClient);
	}

	/**
	 * @param baseUri
	 *            base URI <em>without</em> the port number, cannot be {@code null}
	 * @param user
	 *            cannot be {@code null}
	 * @param pass
	 *            cannot be {@code null}
	 * @param publicClient
	 *            cannot be {@code null} and is assumed to be started when the first invocation of {@link HomeServerInternalApiClient} method is made.
	 * @throws URISyntaxException
	 */
	public HomeServerInternalApiClient(URI baseUri, String user, String pass, HomeServerPublicApiClient publicClient) throws URISyntaxException {
		super(new URI(baseUri.getScheme(), null, baseUri.getHost(),  HOME_SERVER_INTERNAL_API_PORT, null, null, null), user, pass);
		assert publicClient != null;
		this.publicClient = publicClient;
	}

	/**
	 * 
	 * @param newTemp
	 *            in 1/10 degree Celsius, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return the new scald temperature in in 1/10 degree Celsius, or {@code null}; never {@code null} (the value is a {@link Short} for mocking purposes)
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
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
				log.log(Level.SEVERE, "Setting scald temperature failed: no result returned");
				throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR);
			}
			final String confirmedTemp = result.response.data;
			short value = Short.parseShort(confirmedTemp.substring(2));
			System.out.println("New scald temperature = " + value);
			return value;
		}
		throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR);
	}

	/**
	 * 
	 * @param deviceID
	 * @param previousTemp
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public void clearScaldProtection(String deviceID, int previousTemp) throws ClientException {
		assert previousTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		doPost("/cmd/VF/" + deviceID, "data=0", new int[] { HttpStatus.OK_200 });
		publicClient.setReferenceTemperature(deviceID, previousTemp);
	}
}
