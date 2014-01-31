package elm.sim.hs.client;

import java.net.URISyntaxException;
import java.util.logging.Logger;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import elm.sim.hs.model.Device;
import elm.sim.hs.model.HomeServerResponse;

public abstract class AbstractCommandLineClient {

	private static final Logger LOG = Logger.getLogger(AbstractCommandLineClient.class.getName());

	private static void initSslContextFactory(HttpClient client) {
		SslContextFactory factory = client.getSslContextFactory();
		if (factory != null) {
			factory.setTrustAll(true);
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		String user = HomeServerPublicApiClient.HOME_SERVER_ADMIN_USER;
		String password = HomeServerPublicApiClient.HOME_SERVER_DEFAULT_PASSWORD;
		String baseUri = HomeServerPublicApiClient.DEFAULT_HOME_SERVER_URI;
		String deviceID = null;
		boolean useInternalClient = true;
		boolean verbose = false;

		try {
			int i = 0;
			while (i < args.length) {
				String flag = args[i++];
				if ("-pass".equals(flag)) {
					password = getArgument(flag, args, i++);
				} else if ("-uri".equals(flag)) {
					baseUri = getArgument(flag, args, i++);
				} else if ("-device".equals(flag)) {
					deviceID = getArgument(flag, args, i++);
				} else if ("-nointernal".equals(flag)) {
					useInternalClient = false;
				} else if ("-verbose".equals(flag)) {
					verbose = true;
				} else if (flag.startsWith("-")){
					throw new IllegalArgumentException("Unknown flag '" + flag + "'");
				} else {
					throw new IllegalArgumentException("Unexpected argument '" + flag + "'");
				}
			}
		} catch (IllegalArgumentException ex) {
			System.err.println("Usage: " + AbstractCommandLineClient.class.getName() + " [-pass password] [-uri <baseURI>] [-device ID] [-nointernal] [-verbose]");
			System.err.println("       (" + ex.getMessage() + ")");
			System.exit(1);
		}

		HomeServerPublicApiClient publicClient = new HomeServerPublicApiClient(baseUri, user, password);
		initSslContextFactory(publicClient.getClient());

		HomeServerInternalApiClient internalClient = null;
		if (useInternalClient) {
			internalClient = new HomeServerInternalApiClient(baseUri, user, password, publicClient);
		}

		try {
			publicClient.start();

			HomeServerResponse response = publicClient.getServerStatus();
			if (verbose) {
				print(publicClient, response);
			}

			response = publicClient.getRegisteredDevices();
			if (response != null) {
				for (Device dev : response.devices) {
					if (!dev._isAlive()) {
						LOG.warning("Registered Device " + dev.id + ": " + dev._getError().name());
						// Do not send request with this device ID.
					} else if (!dev._isOk()) {
						LOG.warning("Registered Device " + dev.id + ": " + dev._getError());
					}
				}
				if (verbose) {
					print(publicClient, response);
				}

				if (deviceID != null && response._isDeviceAlive(deviceID)) {
					response = publicClient.getDeviceStatus(deviceID);
					if (verbose) {
						print(publicClient, response);
					}

					// Change demand temperature:
					publicClient.setReferenceTemperature(deviceID, 190);

					Short demandTemp = publicClient.getReferenceTemperature(deviceID);
					System.out.println("Reference temp (setpoint) = " + demandTemp);

					if (useInternalClient) {
						internalClient.start();
						internalClient.setScaldProtectionTemperature(deviceID, 310);
					}
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

	protected static void print(HomeServerPublicApiClient publicClient, HomeServerResponse response) {
		System.out.println("\n----");
		System.out.println(publicClient.getGson().toJson(response));
	}

	protected static String getArgument(String flag, String[] args, int i) {
		if (i < args.length) {
			return args[i];
		}
		throw new IllegalArgumentException("Missing argument(s) for flag '" + flag + "'");
	}
}
