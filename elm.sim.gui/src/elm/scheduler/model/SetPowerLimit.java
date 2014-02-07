package elm.scheduler.model;

import java.util.logging.Logger;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;

public class SetPowerLimit extends AbstractDeviceUpdate {
	final int approvedPowerLimitWatt;

	/**
	 * <p><em>Note: </em></p>
	 * @param device
	 * @param approvedPowerWatt can be {@link DeviceInfo#UNLIMITED_POWER} which is a negative value.
	 */
	public SetPowerLimit(DeviceInfo device, int approvedPowerWatt) {
		super(device, true);
		assert approvedPowerWatt == DeviceInfo.UNLIMITED_POWER || approvedPowerWatt >= 0;
		this.approvedPowerLimitWatt = approvedPowerWatt;
	}

	public int getApprovedPowerLimitWatt() {
		return approvedPowerLimitWatt;
	}

	@Override
	public void run(HomeServerInternalApiClient client, Logger log) throws ClientException {
		if (approvedPowerLimitWatt == DeviceInfo.UNLIMITED_POWER || approvedPowerLimitWatt > 0) {
			getDevice().powerConsumptionApproved(approvedPowerLimitWatt);
		} else {
			getDevice().powerConsumptionDenied();
		}
		@SuppressWarnings("unused")
		int actualValue = client.setScaldProtectionTemperature(getDevice().getId(), getDevice().getScaldTemperature());
		log.info("Device " + getDevice().getId() + ": approved power limit: " + approvedPowerLimitWatt + " W, scald temperature: "
				+ getDevice().getScaldTemperature() + "°C");
	}
}