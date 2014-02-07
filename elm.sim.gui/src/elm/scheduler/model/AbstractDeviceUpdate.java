package elm.scheduler.model;

import java.util.logging.Logger;

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

	/**
	 * Executes the updates.
	 * 
	 * @param client
	 *            cannot be {@code null}
	 * @param log
	 *            cannot be {@code null}
	 */
	public abstract void run(HomeServerInternalApiClient client, Logger log) throws ClientException;

	public DeviceInfo getDevice() {
		return device;
	}

	public boolean isUrgent() {
		return urgent;
	}
}
