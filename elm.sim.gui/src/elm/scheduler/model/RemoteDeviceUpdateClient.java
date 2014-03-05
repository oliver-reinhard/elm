package elm.scheduler.model;

import elm.util.ClientException;

/**
 * This interface defines the methods of a client used to perform {@link RemoteDeviceUpdate}s at a remote server.
 */
public interface RemoteDeviceUpdateClient {

	/**
	 * 
	 * @param newTemperatureUnits
	 *            in 1/10°Cs, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return the new scald temperature in in 1/10 °C, or {@code null}; never {@code null} (the value is a {@link Short} for mocking purposes)
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	Short setScaldProtectionTemperature(String deviceID, int newTemperatureUnits) throws ClientException;

	/**
	 * 
	 * @param deviceID
	 * @param previousTemperatureUnits in 1/10°C, can be {@code null}
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void clearScaldProtection(String deviceID, Integer previousTemperatureUnits) throws ClientException;

}