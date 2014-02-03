package elm.scheduler.model;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;

public class DeviceUpdate {

	protected final DeviceInfo device;

	public DeviceUpdate(DeviceInfo device) {
		this.device = device;
	}

	public void run(HomeServerInternalApiClient client) throws ClientException {
		// do nothing
	}

}
