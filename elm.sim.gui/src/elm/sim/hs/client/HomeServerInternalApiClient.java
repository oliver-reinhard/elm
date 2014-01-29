package elm.sim.hs.client;

import java.net.URISyntaxException;
import java.util.logging.Level;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;

import elm.sim.hs.model.HomeServerResponse;

public class HomeServerInternalApiClient extends AbstractHomeServerClient {

	/** The default port for internal Home Server access according to API v1.0 documentation. */
	private static final String HOME_SERVER_INTERNAL_API_PORT_SUFFIX = ":8080";

	protected final HomeServerPublicApiClient publicClient;

	/**
	 * @param pass
	 *            cannot be {@code null}
	 * @param publicClient
	 *            cannot be {@code null} and is assumed to be started when the first invocation of {@link HomeServerInternalApiClient} method is made.
	 * @throws URISyntaxException
	 */
	public HomeServerInternalApiClient(String pass, HomeServerPublicApiClient publicClient) throws URISyntaxException {
		this(HomeServerPublicApiClient.DEFAULT_HOME_SERVER_URI + HOME_SERVER_INTERNAL_API_PORT_SUFFIX, HOME_SERVER_ADMIN_USER, pass, publicClient);
	}

	/**
	 * @param baseUri base URI <em>without</em> the port number, cannot be {@code null}
	 * @param user
	 * @param pass
	 *            cannot be {@code null}
	 * @param publicClient
	 *            cannot be {@code null} and is assumed to be started when the first invocation of {@link HomeServerInternalApiClient} method is made.
	 * @throws URISyntaxException
	 */
	public HomeServerInternalApiClient(String baseUri, String user, String pass, HomeServerPublicApiClient publicClient)
			throws URISyntaxException {
		super(baseUri + HOME_SERVER_INTERNAL_API_PORT_SUFFIX, user, pass);
		assert publicClient != null;
		this.publicClient = publicClient;
	}

	/**
	 * 
	 * @param newTemp
	 *            in 1/10 degree Celsius, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return the new scald temperature in in 1/10 degree Celsius, or {@code null}
	 */
	public Integer setScaldProtectionTemperature(String deviceID, int newTemp) {
		assert newTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		if (!publicClient.setReferenceTemperature(deviceID, newTemp)) {
			return null;
		}
		doPost("/cmd/VF/" + deviceID, "data=1", new int[] { HttpStatus.OK_200 });
		// scald protection value is in FULL DEGREES Celcius!
		ContentResponse response = doPost("/cmd/Vv/" + deviceID, "data=" + (newTemp / 10), new int[] { HttpStatus.OK_200 });
		if (response != null) {
			final HomeServerResponse result = getGson().fromJson(response.getContentAsString(), HomeServerResponse.class);
			if (result.response == null || result.response.data == null) {
				LOG.log(Level.SEVERE, "Setting scald temperature failed: no result returned");
				return null;
			}
			final String confirmedTemp = result.response.data;
			int value = Integer.parseInt(confirmedTemp.substring(2));
			System.out.println("New scald temp = " + value);
			return value;
		}
		return null;
	}

	public boolean clearScaldProtection(String deviceID, int previousTemp) {
		assert previousTemp >= 0;
		assert deviceID != null && !deviceID.isEmpty();

		if (doPost("/cmd/VF/" + deviceID, "data=0", new int[] { HttpStatus.OK_200 }) == null) {
			return false;
		}
		return publicClient.setReferenceTemperature(deviceID, previousTemp);
	}
}
