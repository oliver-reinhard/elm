package elm.scheduler.model;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;

public class DeviceUpdate {

	private final DeviceInfo device;
	
	private final boolean urgent;

	public DeviceUpdate(DeviceInfo device, boolean urgent) {
		assert device != null;
		this.device = device;
		this.urgent = urgent;
	}

	public void run(HomeServerInternalApiClient client) throws ClientException {
		// do nothing
	}

	public DeviceInfo getDevice() {
		return device;
	}

	public boolean isUrgent() {
		return urgent;
	}
}
