package elm.scheduler.model;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;

public class SetPowerLimit extends AbstractDeviceUpdate {
	final int actualPowerLimitWatt;

	public SetPowerLimit(DeviceInfo device, int actualPowerLimit) {
		super(device, true);
		this.actualPowerLimitWatt = actualPowerLimit;
	}

	@Override
	public void run(HomeServerInternalApiClient client) throws ClientException {
		int actualValue = DeviceInfo.UNLIMITED_POWER;
		// TODO set power limit
		// actualValue = client.setScaldProtectionTemperature(device.getId(), actualPowerLimitWatt);
		getDevice().setActualPowerWatt(actualValue);
		//log.info("Device " + getDevice().getId() + ": set actual power limit to" + actualPowerLimitWatt + "[W]");
	}
}