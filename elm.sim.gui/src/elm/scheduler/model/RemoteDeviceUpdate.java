package elm.scheduler.model;

import java.util.logging.Logger;

import elm.hs.api.model.Device;
import elm.util.ClientException;

public class RemoteDeviceUpdate {

	/** {@link Device#id}. */
	private final String id;
	
	/** The new scald-protection temperature, in [1/10°C]. */
	private Short scaldProtectionTemperature;
	
	private boolean clearScaldProtectionFlag;
	
	/** The temperature before scald protection became effective, in [1/10°C]. */
	private Short previousDemandTemperature;

	/**
	 * @param deviceId
	 *            cannot be {@code null} or empty
	 */
	public RemoteDeviceUpdate(String deviceId) {
		assert deviceId != null && ! deviceId.isEmpty();
		this.id = deviceId;
	}

	public String getId() {
		return id;
	}

	/**
	 * Sets the scald-protection temperature (i.e. a hard <em>upper</em> temperature limit) on the physical device.
	 * 
	 * @param temperature
	 *            the new scald-protection temperature in [1/10°C]
	 */
	public void setScaldProtectionTemperature(short temperature) {
		assert temperature != DeviceController.UNDEFINED_TEMPERATURE && temperature > 0;
		this.clearScaldProtectionFlag = false;
		this.scaldProtectionTemperature = temperature;
		this.previousDemandTemperature = null;
	}

	/**
	 * Clears the scald-protection (i.e. the hard <em>upper</em> temperature limit) the on the physical device and restore the reference temperature as set by
	 * the user before scald-protection became effective.
	 * 
	 * @param previousDemandTemperature
	 *            reference temperature as set by the user before scald-protection became effective in [1/10°C]
	 */
	public void clearScaldProtection(Short previousDemandTemperature) {
		assert previousDemandTemperature == null || previousDemandTemperature != DeviceController.UNDEFINED_TEMPERATURE && previousDemandTemperature > 0;
		this.clearScaldProtectionFlag = true;
		this.previousDemandTemperature = previousDemandTemperature;
		this.scaldProtectionTemperature = null;
	}

	/**
	 * Executes the updates.
	 * 
	 * @param client
	 *            cannot be {@code null}
	 * @param log
	 *            cannot be {@code null}
	 */
	public void execute(RemoteDeviceUpdateClient client, Logger log) throws ClientException {
		if (scaldProtectionTemperature != null) {
			short actualValue = (short) client.setScaldProtectionTemperature(id, scaldProtectionTemperature);
			log.info("Device " + id + ": scald-protection temperature set to: " + (actualValue / 10) + "°C");

		} else if (clearScaldProtectionFlag) {
			client.clearScaldProtection(id, previousDemandTemperature == null ? null : new Integer(previousDemandTemperature));
			log.info("Device " + id + ": cleared scald-protection");
		}
	}
}
