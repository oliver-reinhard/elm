package elm.scheduler.model;

import java.util.logging.Logger;

import elm.ui.api.ElmStatus;
import elm.ui.api.ElmUserFeedback;
import elm.util.ClientException;

public class AsynchRemoteDeviceUpdate {

	private DeviceController device;
	private ElmUserFeedback feedback;
	/** The new scald-protection temperature, in [1/10°C]. */
	private Short scaldProtectionTemperature;
	private boolean clearScaldProtectionFlag;
	/** The temperature before scald protection became effective, in [1/10°C]. */
	private Short previousDemandTemperature;

	/**
	 * @param device
	 *            device that should have its parameters changed, cannot be {@code null}
	 */
	public AsynchRemoteDeviceUpdate(DeviceController device) {
		assert device != null;
		this.device = device;
	}

	/**
	 * @param device
	 *            device that should have its parameters changed, cannot be {@code null}
	 * @param deviceStatus
	 *            new device status, cannot be {@code null}
	 */
	public AsynchRemoteDeviceUpdate(DeviceController device, ElmStatus deviceStatus) {
		this(device);
		feedback = new ElmUserFeedback(device.getId(), deviceStatus);
	}

	/**
	 * Updates the user feedback for all the devices managed by the {@link HomeServer}.
	 * 
	 * @param schedulerStatus
	 *            cannot be {@code null}
	 */
	public AsynchRemoteDeviceUpdate(ElmStatus schedulerStatus) {
		feedback = new ElmUserFeedback(schedulerStatus);
	}

	public DeviceController getDevice() {
		return device;
	}

	public boolean isUrgent() {
		return scaldProtectionTemperature != null || previousDemandTemperature != null;
	}

	public void setFeedback(ElmUserFeedback feedback) {
		this.feedback = feedback;
	}

	public ElmUserFeedback getFeedback() {
		return feedback;
	}

	/**
	 * Sets the scald-protection temperature (i.e. a hard <em>upper</em> temperature limit) on the physical device.
	 * 
	 * @param temperature
	 *            the new scald-protection temperature in [1/10°C]
	 */
	public void setScaldProtectionTemperature(short temperature) {
		assert temperature != DeviceController.UNDEFINED_TEMPERATURE && temperature > 0;
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
	public void execute(RemoteDeviceUpdateClient client, Logger log) throws ClientException {
		if (scaldProtectionTemperature != null) {
			short actualValue = (short) client.setScaldProtectionTemperature(getDevice().getId(), scaldProtectionTemperature);
			log.info("Device " + getDevice().getId() + ": scald-protection temperature set to: " + (actualValue / 10) + "°C");
			
		} else if (clearScaldProtectionFlag) {
			client.clearScaldProtection(device.getId(), previousDemandTemperature == null ? null : new Integer(previousDemandTemperature));
			log.info("Device " + getDevice().getId() + ": cleared scald-protection");
		}

		if (feedback != null) {
			client.updateUserFeedback(feedback);
			if (getDevice() != null) {
				log.info("Device " + getDevice().getId() + ": updated user feedback: device status " + feedback.deviceStatus);
			} else {
				log.info("Updated user feedback: scheduler status " + feedback.deviceStatus);
			}
		}
	}
}
