package elm.hs.api;

import java.net.URI;

import elm.scheduler.model.RemoteDeviceUpdate;
import elm.util.ClientException;

/**
 * This interface defines the service methods available to a client needed to perform {@link RemoteDeviceUpdate}s at a remote server.
 */
public interface HomeServerInternalService {

	/** The default port for internal Home Server access according to API v1.0 documentation. */
	static final int INTERNAL_API_PORT = 8080;

	/** The default URI according to API v1.0 documentation. */
	static final URI INTERNAL_API_URI = URI.create(HomeServerService.DEFAULT_URI.toString() + ":" + INTERNAL_API_PORT);

	/**
	 * Sets the scald-protection flag and temperature and returns the reference temperature currently set by the user.
	 * 
	 * @param newTemperatureUnits
	 *            in 1/10°C, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return the new scald temperature in in 1/10 °C, or {@code null}; (the value is a {@link Short} for testing / mocking purposes)
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	Short setScaldProtectionTemperature(String deviceID, int newTemperatureUnits) throws ClientException;

	/**
	 * Clears the scald-protection flag and temperature and restores the reference temperature set by the user prior to the activation of scald protection.
	 * 
	 * @param deviceID
	 * @param previousTemperatureUnits
	 *            in 1/10°C, can be {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void clearScaldProtection(String deviceID, Integer previousTemperatureUnits) throws ClientException;

}