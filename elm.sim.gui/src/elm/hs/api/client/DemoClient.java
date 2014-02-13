package elm.hs.api.client;

import java.net.URISyntaxException;

import org.apache.commons.cli.Options;

import elm.hs.api.model.Device;
import elm.hs.api.model.HomeServerResponse;
import elm.util.ClientUtil;

public class DemoClient extends AbstractCommandLineClient {

	@Override
	protected void addCommandLineOptions(Options options) {
		super.addCommandLineOptions(options);
		options.getOption(OPT_DEVICE).setRequired(true);

	}

	protected void run() throws URISyntaxException {
		HomeServerPublicApiClient publicClient = new HomeServerPublicApiClient(baseUri, user, password);
		ClientUtil.initSslContextFactory(publicClient.getClient());

		HomeServerInternalApiClient internalClient = null;
		if (useInternalClient) {
			internalClient = new HomeServerInternalApiClient(baseUri, user, password, publicClient);
		}

		try {
			publicClient.start();

			HomeServerResponse response = publicClient.getServerStatus();
			if (verbose) {
				print("Server Status", publicClient, response);
			}

			response = publicClient.getRegisteredDevices();
			for (Device dev : response.devices) {
				if (!dev._isAlive()) {
					LOG.warning("Registered Device " + dev.id + ": " + dev._getError().name());
					// Do not send request with this device ID.
				} else if (!dev._isOk()) {
					LOG.warning("Registered Device " + dev.id + ": " + dev._getError());
				}
			}
			if (verbose) {
				print("Registered Devices", publicClient, response);
			}

			if (deviceID != null && response._isDeviceAlive(deviceID)) {
				response = publicClient.getDeviceStatus(deviceID);
				if (verbose) {
					print("Device Status of " + deviceID, publicClient, response);
				}

				// Change demand temperature:
				int referenceTemperature = 190;
				publicClient.setReferenceTemperature(deviceID, referenceTemperature);
				System.out.println("\n---- Set Reference Temperature (setpoint) := " + referenceTemperature + " ----");

				Short actualTemperature = publicClient.getReferenceTemperature(deviceID);
				System.out.println("\n---- Get Reference Temperature (setpoint) = " + actualTemperature + " ----");

				if (useInternalClient) {
					internalClient.start();
					int scaldProtectionTemperature = 310;
					System.out.println("\n---- Set Scald-Protection Temperature = " + scaldProtectionTemperature + " ----");
					internalClient.setScaldProtectionTemperature(deviceID, scaldProtectionTemperature);
				}
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

	protected static void print(String title, HomeServerPublicApiClient publicClient, HomeServerResponse response) {
		System.out.println("\n---- " + title + " ----");
		System.out.println(publicClient.getGson().toJson(response));
	}

	public static void main(String[] args) throws Exception {
		DemoClient client = new DemoClient();
		client.parseCommandLine(args);
		client.run();
	}
}
