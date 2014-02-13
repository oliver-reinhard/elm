package elm.scheduler.model;

import java.util.logging.Logger;

import elm.hs.api.client.ClientException;
import elm.ui.api.ElmDeviceUserFeedback;

public class AsynchronousPhysicalDeviceUpdate {

	private final DeviceInfo device;
	private ElmDeviceUserFeedback feedback;
	/** The new scald-protection temperature, in [1/10°C]. */
	private Short scaldProtectionTemperature;
	/** The temperature before scald protection became effective, in [1/10°C]. */
	private Short previousDemandTemperature;;

	/**
	 * @param device
	 *            device that should have its power limit changed
	 */
	public AsynchronousPhysicalDeviceUpdate(DeviceInfo device) {
		assert device != null;
		this.device = device;
	}

	public DeviceInfo getDevice() {
		return device;
	}

	public boolean isUrgent() {
		return scaldProtectionTemperature != null || previousDemandTemperature != null;
	}

	public void setFeedback(ElmDeviceUserFeedback feedback) {
		this.feedback = feedback;
	}

	/**
	 * Sets the scald-protection temperature (i.e. a hard <em>upper</em> temperature limit) on the physical device.
	 * 
	 * @param temperature
	 *            the new scald-protection temperature in [1/10°C]
	 */
	public void setScaldProtectionTemperature(short temperature) {
		assert temperature != DeviceInfo.UNDEFINED_TEMPERATURE && temperature > 0;
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
	public void clearScaldProtection(short previousDemandTemperature) {
		assert previousDemandTemperature != DeviceInfo.UNDEFINED_TEMPERATURE && previousDemandTemperature > 0;
		this.previousDemandTemperature = previousDemandTemperature;
		this.scaldProtectionTemperature = null;
	}

	public boolean isVoid() {
		return scaldProtectionTemperature == null && previousDemandTemperature == null && feedback == null;
	}

	/**
	 * Executes the updates.
	 * 
	 * @param client
	 *            cannot be {@code null}
	 * @param log
	 *            cannot be {@code null}
	 */
	public void execute(PhysicalDeviceUpdateClient client, Logger log) throws ClientException {
		if (scaldProtectionTemperature != null) {
			short actualValue = (short) client.setScaldProtectionTemperature(getDevice().getId(), scaldProtectionTemperature);
			log.info("Device " + getDevice().getId() + ": scald-protection temperature set to: " + (actualValue / 10) + "°C");
		} else if (previousDemandTemperature != null) {
			client.clearScaldProtection(device.getId(), previousDemandTemperature);
		}

		if (feedback != null) {
			client.updateUserFeedback(feedback);
		}
	}
}
