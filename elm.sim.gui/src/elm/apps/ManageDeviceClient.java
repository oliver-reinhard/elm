package elm.apps;

import java.net.URISyntaxException;
import java.util.logging.Level;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import elm.hs.api.Device;
import elm.hs.api.HomeServerResponse;
import elm.hs.api.client.AbstractCommandLineClient;
import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.client.HomeServerPublicApiClient;
import elm.util.ClientException;
import elm.util.ClientUtil;
import elm.util.ElmLogFormatter;

public class ManageDeviceClient extends AbstractCommandLineClient {

	protected static final String OPT_CMD = "cmd";
	protected static final String OPT_TEMPERATURE = "t";

	private HomeServerPublicApiClient publicClient;
	private HomeServerInternalApiClient internalClient;
	private Command cmd = Command.LIST;
	private int temperatureCelcius;

	enum Command {
		STATUS("status"), LIST("ls"), POLL("poll"), ADD("add"), REMOVE("rm"), SET("set"), REDUCE("reduce"), UNREDUCE("unreduce");

		private final String cmd;

		Command(String cmd) {
			assert cmd != null && !cmd.isEmpty();
			this.cmd = cmd;
		}

		public String getCmd() {
			return cmd;
		}
	}

	@Override
	protected void addCommandLineOptions(Options options) {
		super.addCommandLineOptions(options);

		boolean first = true;
		StringBuilder commands = new StringBuilder();
		for (Command cmd : Command.values()) {
			if (!first) {
				commands.append(" | ");
			}
			commands.append(cmd.getCmd());
			first = false;
		}

		options.addOption(OPT_CMD, "command", true, "arg: " + commands);
		options.addOption(OPT_TEMPERATURE, "temperature", true, "temperature in °C");
	}

	@Override
	protected void processOptions(CommandLine line) throws ParseException {
		super.processOptions(line);
		String cmdStr = line.getOptionValue(OPT_CMD);
		this.cmd = null;
		for (Command cmd : Command.values()) {
			if (cmd.getCmd().equals(cmdStr)) {
				this.cmd = cmd;
				break;
			}
		}
		if (this.cmd == null) {
			throw new ParseException("Unrecognized command: " + cmdStr);
		}

		String temperatureStr = line.getOptionValue(OPT_TEMPERATURE);
		if (this.cmd == Command.SET || this.cmd == Command.REDUCE || this.cmd == Command.UNREDUCE) {
			if (temperatureStr == null) {
				throw new ParseException("temperature missing (-" + OPT_TEMPERATURE + ")");
			}
			temperatureCelcius = Integer.parseInt(temperatureStr);
			useInternalClient = true;
		}
	}

	protected void run() throws URISyntaxException {

		publicClient = new HomeServerPublicApiClient(publicBaseUri, user, password);
		ClientUtil.initSslContextFactory(publicClient.getClient());
		publicClient.setLogLevel(verbose ? Level.INFO : Level.SEVERE);

		internalClient = null;
		if (useInternalClient) {
			internalClient = new HomeServerInternalApiClient(user, password, publicClient);
//			ClientUtil.initSslContextFactory(internalClient.getClient());
			internalClient.setLogLevel(verbose ? Level.INFO : Level.SEVERE);
		}

		try {
			publicClient.start();
			if (useInternalClient) {
				internalClient.start();
			}

			if (cmd == Command.STATUS) {
				cmdStatus();
				
			} else if(deviceID != null) {

				switch (cmd) {
				case LIST:
					cmdList();
					break;
				case POLL:
					cmdPoll();
					break;
				case ADD:
					cmdAdd();
					break;
				case REMOVE:
					cmdRemove();
					break;
				case SET:
					cmdSet();
					break;
				case REDUCE:
					cmdReduce();
					break;
				case UNREDUCE:
					cmdUnreduce();
					break;
				default:
					throw new IllegalArgumentException("Illegeal command: " + cmd);
				}

			} else {
				listAll();
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

	private void cmdStatus() throws ClientException {
		HomeServerResponse response = publicClient.getServerStatus();
		print("Server Status", publicClient, response);
	}

	private void cmdList() throws ClientException {
		HomeServerResponse response = publicClient.getDeviceStatus(deviceID);
		print("Device Status of " + deviceID, publicClient, response);
	}

	private void cmdPoll() throws ClientException {
		while (true) {
			cmdList();
			synchronized (this) {
				try {
					wait(2000);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private void cmdAdd() throws ClientException {
		publicClient.manageDevice(deviceID);
		cmdList();
	}

	private void cmdRemove() throws ClientException {
		publicClient.unmanageDevice(deviceID);
		listAll();
	}

	private void cmdSet() throws ClientException {
		publicClient.setReferenceTemperature(deviceID, temperatureCelcius*10);
		cmdList();
	}

	private void cmdReduce() throws ClientException {
		Short actualTemperatureUnits = internalClient.setScaldProtectionTemperature(deviceID, temperatureCelcius*10);
		LOG.info("Actual scald-protection temperature: " + actualTemperatureUnits/10 + "°C");
		cmdList();
	}

	private void cmdUnreduce() throws ClientException {
		internalClient.clearScaldProtection(deviceID, temperatureCelcius*10);
		cmdList();
	}

	private void listAll() throws ClientException {
		HomeServerResponse response = publicClient.getAllDevices();
		if (response.devices != null) {
			print("All Devices", publicClient, response);
			for (Device dev : response.devices) {
				if (!dev._isAlive()) {
					LOG.warning("All devices: Device " + dev.id + ": " + dev._getError().name());
					// Do not send request with this device ID.
				} else if (!dev._isOk()) {
					LOG.warning("All devices: Device " + dev.id + ": " + dev._getError());
				}
			}
		}
	}

	protected static void print(String title, HomeServerPublicApiClient publicClient, HomeServerResponse response) {
		System.out.println("\n---- " + title + " ----");
		System.out.println(publicClient.getGson().toJson(response));
	}

	public static void main(String[] args) throws Exception {
		ElmLogFormatter.init();
		ManageDeviceClient client = new ManageDeviceClient();
		client.parseCommandLine(args);
		System.out.println("Cmd = " + client.cmd);
		client.run();
	}
}
