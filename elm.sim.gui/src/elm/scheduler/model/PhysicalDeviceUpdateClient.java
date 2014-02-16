package elm.scheduler.model;

import elm.ui.api.ElmUserFeedback;
import elm.util.ClientException;

public interface PhysicalDeviceUpdateClient {

	/**
	 * 
	 * @param newTemp
	 *            in 1/10 degree Celsius, cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return the new scald temperature in in 1/10 degree Celsius, or {@code null}; never {@code null} (the value is a {@link Short} for mocking purposes)
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	Short setScaldProtectionTemperature(String deviceID, int newTemp) throws ClientException;

	/**
	 * 
	 * @param deviceID
	 * @param previousTemp
	 * @throws ClientException if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void clearScaldProtection(String deviceID, int previousTemp) throws ClientException;
	
	void updateUserFeedback(ElmUserFeedback feedback) throws ClientException;

}