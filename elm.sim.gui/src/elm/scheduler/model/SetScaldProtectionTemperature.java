package elm.scheduler.model;

import java.util.logging.Logger;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;

/**
 * Instances of this class set the scald-protection temperature (i.e. a hard <em>upper</em> temperature limit) on the physical device.
 */
public class SetScaldProtectionTemperature extends AsynchronousDeviceUpdate {

	private final short scaldProtectionTemperature;

	/**
	 * @param device
	 *            device that should have its power limit changed
	 * @param temperature
	 *            the new scald-protection temperature in [1/10°C]
	 */
	public SetScaldProtectionTemperature(DeviceInfo device, short temperature) {
		super(device, true);
		assert temperature != DeviceInfo.UNDEFINED_TEMPERATURE && temperature > 0;
		this.scaldProtectionTemperature = temperature;
	}

	@Override
	public void run(HomeServerInternalApiClient client, Logger log) throws ClientException {
		short actualValue = (short) client.setScaldProtectionTemperature(getDevice().getId(), scaldProtectionTemperature);
		log.info("Device " + getDevice().getId() + ": scald-protection temperature set to: " + (actualValue / 10) + "°C");
	}
}