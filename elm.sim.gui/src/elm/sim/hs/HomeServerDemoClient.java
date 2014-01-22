package elm.sim.hs;

import java.net.URISyntaxException;
import java.util.logging.Logger;

import elm.sim.hs.model.Device;
import elm.sim.hs.model.HomeServerResponse;
import static elm.sim.hs.AbstractHomeServerClient.HOME_SERVER_DEFAULT_PASSWORD;

public class HomeServerDemoClient {

	private static final Logger LOG = Logger.getLogger(HomeServerDemoClient.class.getName());

	public static void main(String[] args) throws URISyntaxException {

		HomeServerPublicApiClient publicClient = new HomeServerPublicApiClient(HOME_SERVER_DEFAULT_PASSWORD);
		HomeServerInternalApiClient internalClient = new HomeServerInternalApiClient(HOME_SERVER_DEFAULT_PASSWORD, publicClient);

		try {
			publicClient.start();

			// ContentResponse response = client.GET("http://localhost:8080/hs?action");
			publicClient.getServerStatus();

			HomeServerResponse response = publicClient.getRegisteredDevices();
			if (response != null) {
				for (Device dev : response.devices) {
					if (!dev.isAlive()) {
						LOG.warning("Registered Device " + dev.id + " is not connected.");
						// Do not send request with this device ID.
					}
				}
			}

			String deviceID = "A001FFFF8A";
			if (response.isDeviceAlive(deviceID)) {
				publicClient.getDeviceStatus(deviceID);

				// Change demand temperature:
				publicClient.setDemandTemperature(deviceID, 190);

				Short demandTemp = publicClient.getDemandTemperature(deviceID);
				System.out.println("Demand temp = " + demandTemp);

				internalClient.start();
				internalClient.setScaldProtectionTemperature(deviceID, 310);
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (publicClient != null) {
					publicClient.stop();
				}
				if (internalClient != null) {
					internalClient.stop();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
