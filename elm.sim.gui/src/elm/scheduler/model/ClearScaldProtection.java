package elm.scheduler.model;

import java.util.logging.Logger;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;

/**
 * Instances of this class clear the scald-protection (i.e. the hard <em>upper</em> temperature limit) the on the physical device and restore the reference
 * temperature as set by the user before scald-protection became effective.
 */
public class ClearScaldProtection extends AsynchronousDeviceUpdate {

	private final short previousDemandTemperature;

	/**
	 * @param device
	 *            device that should have its power limit removed
	 * @param previousDemandTemperature
	 *            reference temperature as set by the user before scald-protection became effective in [1/10°C]
	 */
	public ClearScaldProtection(DeviceInfo device, short previousDemandTemperature) {
		super(device, true);
		assert previousDemandTemperature > 0;
		this.previousDemandTemperature = previousDemandTemperature;
	}

	@Override
	public void run(HomeServerInternalApiClient client, Logger log) throws ClientException {
		client.clearScaldProtection(getDevice().getId(), previousDemandTemperature);
	}
}