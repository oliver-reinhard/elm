package elm.sim.hs.server;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.hs.api.model.Device;
import elm.hs.api.model.HomeServerFieldNamingStrategy;
import elm.hs.api.model.HomeServerResponse;
import elm.hs.api.model.Info;
import elm.hs.api.model.Log;
import elm.hs.api.model.Server;
import elm.hs.api.model.Service;
import elm.hs.api.model.Status;

public class HomeServerDB {

	private final List<Service> services = new ArrayList<Service>();
	private final List<Device> devicesInfo = new ArrayList<Device>();
	private final List<Device> devicesStatus = new ArrayList<Device>();

	public HomeServerDB(String url) {
		assert url != null && !url.isEmpty();

		// Services
		Service s = new Service();
		s.deviceList = Service.DEVICES_PATH;
		services.add(s);

		s = new Service();
		s.deviceStatus = Service.STATUS_PATH;
		services.add(s);

		s = new Service();
		s.deviceSetpoint = Service.SETPOINT_PATH;
		services.add(s);

		s = new Service();
		s.deviceLogs = Service.LOGS_PATH;
		services.add(s);

		s = new Service();
		s.fileList = Service.FILES_PATH;
		services.add(s);

		s = new Service();
		s.timerList = Service.TIMERS_PATH;
		services.add(s);

		// Devices
		devicesInfo.add(createDevice("2016FFFF55", url, (short) 200, true, false, false));
		devicesInfo.add(createDevice("A001FFFF33", url, (short) 380, true, false, false));
		devicesInfo.add(createDevice("6003FFFF1A", url, (short) 420, true, false, false));

		for (Device dev : devicesInfo) {
			devicesStatus.add(createDevice(dev.id, url, dev.info.setpoint, false, true, false));
		}
	}

	/**
	 * Returns the general Home Server status.
	 * 
	 * @return {@code null before initialized}
	 */
	public HomeServerResponse getStatus() {
		return createResponse(true, false, true);
	}

	public HomeServerResponse getDevices() {
		return createResponse(false, true, false);
	}

	/**
	 * 
	 * @param id
	 * @return {@code null} if no device with the given id exists
	 */
	public HomeServerResponse getDeviceStatus(String id) {
		for (Device dev : devicesStatus) {
			if (dev.id.equals(id)) {
				HomeServerResponse response = createResponse(false, false, false);
				response.cached = false;
				response.total = 1;
				response.devices = new ArrayList<Device>();
				response.devices.add(dev);
				return response;
			}
		}
		return null;
	}

	/**
	 * Changes the setpoint of the given devices in the database and returns the proper response.
	 * 
	 * @param id
	 * @param setpoint
	 * @return {@code null} if no device with the given id exists
	 */
	public HomeServerResponse deviceSetpoint(String id, short setpoint) {
		HomeServerResponse response = getDeviceStatus(id);
		if (response != null) {
			Device dev = response.devices.get(0);
			// change the setpoint in the current list!
			dev.status.setpoint = setpoint;
			return response;
		}
		return null;
	}

	public static void print(Object obj) {
		Gson gson = new GsonBuilder().setFieldNamingStrategy(new HomeServerFieldNamingStrategy()).setPrettyPrinting().create();
		System.out.println("JSON: " + gson.toJson(obj));
	}

	protected HomeServerResponse createResponse(boolean putServices, boolean putDevices, boolean putServer) {
		final HomeServerResponse result = new HomeServerResponse();
		result.version = "1.0";
		result.cached = true;
		result.success = true;
		result.error = 0;
		result.time = (int) System.currentTimeMillis();

		if (putServices) {
			result.services = services;
			result.total = services.size();
		}

		if (putDevices) {
			result.devices = devicesInfo;
			result.total = devicesInfo.size();
		}

		if (putServer) {
			result.server = new Server();
			result.server.id = "D4CA6DB451EE";
			result.server.channel = 106;
			result.server.address = 178;
		}

		return result;
	}

	protected Device createDevice(String id, String url, short setpoint, boolean info, boolean status, boolean logs) {
		final Device result = new Device();
		result.id = id;
		result.rssi = -76;
		result.lqi = 15;
		result.connected = true;

		if (info) {
			result.info = new Info();
			result.info.setpoint = setpoint;
			result.info.flags = 1;
			result.info.error = 0;
			result.info.access = 255;
			result.info.activity = 1355266800;
			result.info.url = url;
			result.info.serverCh = 106;
			result.info.serverAddr = 100;
		}

		if (status) {
			result.status = new Status();
			result.status.setpoint = setpoint;
			result.status.tIn = 115;
			result.status.tOut = (short) (setpoint - 1);
			result.status.tP1 = 350;
			result.status.tP2 = 380;
			result.status.tP3 = 420;
			result.status.tP4 = 480;
			result.status.flow = 25;
			result.status.power = 0;
			result.status.powerMax = 180;
			result.status.flags = 0;
			result.status.error = 0;
		}

		// Not supported in API v1.0
		boolean setup = false;
		if (setup) {
			// result.setup = new Setup();
			// result.setup.swVersion = "1.4.1";
			// result.setup.serialDevice = "<serial>";
			// result.setup.serialPowerUnit = "<serial>";
			// result.setup.flowMax = 254;
			// result.setup.loadShedding = 0;
			// result.setup.scaldProtection = 65535;
			// result.setup.fcpAddr = 80;
			// result.setup.powerCosts = 0;
			// result.setup.powerMax = 140;
			// result.setup.calValue = 0;
			// result.setup.timerPowerOn = 300;
			// result.setup.timerLifetime = 172800;
			// result.setup.timerStandby = 2400;
			// result.setup.totalPowerConsumption = 0;
			// result.setup.totalWaterConsumption = 0;
		}
		if (logs) {
			result.logs = new ArrayList<Log>();
			Log log1 = new Log();
			log1.id = 1;
			log1.time = 1355266800;
			log1.length = 10;
			log1.power = 6;
			log1.water = 42;
			log1.cid = 2;
			result.logs.add(log1);

			Log log2 = new Log();
			log2.id = 2;
			log2.time = 1355269200;
			log2.length = 10;
			log2.power = 6;
			log2.water = 38;
			log2.cid = 2;
			result.logs.add(log2);
		}
		return result;
	}

}
