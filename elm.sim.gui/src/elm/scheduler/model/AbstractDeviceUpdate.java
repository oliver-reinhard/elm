package elm.scheduler.model;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;

public abstract class AbstractDeviceUpdate {

	private final DeviceInfo device;
	
	private final boolean urgent;

	public AbstractDeviceUpdate(DeviceInfo device, boolean urgent) {
		assert device != null;
		this.device = device;
		this.urgent = urgent;
	}

	public abstract void run(HomeServerInternalApiClient client) throws ClientException;

	public DeviceInfo getDevice() {
		return device;
	}

	public boolean isUrgent() {
		return urgent;
	}
}
