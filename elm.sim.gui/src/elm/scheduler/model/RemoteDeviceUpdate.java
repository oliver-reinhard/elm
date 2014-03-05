package elm.scheduler.model;

import java.util.logging.Logger;

import elm.hs.api.model.Device;
import elm.util.ClientException;

public class RemoteDeviceUpdate {

	/** {@link Device#id}. */
	private final String id;
	
	/** The new scald-protection temperature, in [1/10°C]. */
	private Short scaldProtectionTemperatureUnits;
	
	private boolean clearScaldProtectionFlag;
	
	/** The temperature before scald protection became effective, in [1/10°C]. */
	private Short previousDemandTemperatureUnits;

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
	 * @param temperatureUnits
	 *            the new scald-protection temperature in [1/10°C]
	 */
	public void setScaldProtectionTemperature(short temperatureUnits) {
		assert temperatureUnits != DeviceController.UNDEFINED_TEMPERATURE && temperatureUnits > 0;
		this.clearScaldProtectionFlag = false;
		this.scaldProtectionTemperatureUnits = temperatureUnits;
		this.previousDemandTemperatureUnits = null;
	}

	/**
	 * Clears the scald-protection (i.e. the hard <em>upper</em> temperature limit) the on the physical device and restore the reference temperature as set by
	 * the user before scald-protection became effective.
	 * 
	 * @param previousDemandTemperatureUnits
	 *            reference temperature as set by the user before scald-protection became effective, in [1/10°C]
	 */
	public void clearScaldProtection(Short previousDemandTemperatureUnits) {
		assert previousDemandTemperatureUnits == null || previousDemandTemperatureUnits != DeviceController.UNDEFINED_TEMPERATURE && previousDemandTemperatureUnits > 0;
		this.clearScaldProtectionFlag = true;
		this.previousDemandTemperatureUnits = previousDemandTemperatureUnits;
		this.scaldProtectionTemperatureUnits = null;
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
		if (scaldProtectionTemperatureUnits != null) {
			log.info("Device " + id + ": setting scald-protection temperature to " + (scaldProtectionTemperatureUnits / 10) + "°C");
			short actualValueUnits = (short) client.setScaldProtectionTemperature(id, scaldProtectionTemperatureUnits);
			if (actualValueUnits == 0) {
				log.severe("Device " + id + ": scald-protection could not be set. Requested: " + (scaldProtectionTemperatureUnits / 10) + "°C");
			}

		} else if (clearScaldProtectionFlag) {
			final Integer previousTemperatureUnits = previousDemandTemperatureUnits == null ? null : new Integer(previousDemandTemperatureUnits);
			final String previousTemperatureCelsius = previousDemandTemperatureUnits == null ? "unknown" : (previousDemandTemperatureUnits/10) + "°C";
			log.info("Device " + id + ": clearing scald protection, restoring previous temperature: " + previousTemperatureCelsius);
			client.clearScaldProtection(id, previousTemperatureUnits);
		}
	}
}
