package elm.hs.api.sim.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.hs.api.model.HomeServerFieldNamingStrategy;
import elm.hs.api.model.HomeServerResponse;
import elm.hs.api.model.Info;
import elm.hs.api.model.Log;
import elm.hs.api.model.Response;
import elm.hs.api.model.Server;
import elm.hs.api.model.Service;
import elm.hs.api.model.Status;
import elm.scheduler.model.UnsupportedModelException;
import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.TapPoint;
import elm.ui.api.ElmUserFeedback;

public class SimHomeServerImpl extends AbstractSimObject implements SimHomeServer{

	private final List<Service> services = new ArrayList<Service>();
	private final Map<String, Device> devices = new LinkedHashMap<String, Device>();
	private final Map<String, DeviceTapPointAdapter> adapters = new HashMap<String, DeviceTapPointAdapter>();

	private IntakeWaterTemperature waterIntakeTemperature = IntakeWaterTemperature.TEMP_10;

	private final URI uri;

	public SimHomeServerImpl(String uri) {
		assert uri != null && !uri.isEmpty();
		this.uri = URI.create(uri); // checks the syntax

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
	}

	@Override
	public SimAttribute[] getSimAttributes() {
		return Attribute.values();
	}

	@Override
	public String getLabel() {
		return uri.toString();
	}

	@Override
	public URI getUri() {
		return uri;
	}

	@Override
	public void setIntakeWaterTemperature(IntakeWaterTemperature newValue) {
		assert newValue != null;
		IntakeWaterTemperature oldValue = waterIntakeTemperature;
		if (oldValue != newValue) {
			waterIntakeTemperature = newValue;
			fireModelChanged(Attribute.INTAKE_WATER_TEMPERATURE, oldValue, newValue);
			for (DeviceTapPointAdapter adapter : adapters.values()) {
				adapter.getDevice().status.tIn = newValue.getUnits();
				adapter.updateTapPoint();
			}
		}
	}

	@Override
	public IntakeWaterTemperature getIntakeWaterTemperature() {
		return waterIntakeTemperature;
	}

	public static SimHomeServer createDemoDB(String uri) {
		SimHomeServerImpl db = new SimHomeServerImpl(uri);
		db.addDevice("2016FFFF55", (short) 200);
		db.addDevice("A001FFFF33", (short) 380);
		db.addDevice("6003FFFF1A", (short) 420);
		return db;
	}

	/**
	 * @param id
	 *            cannot be {@code null} or empty
	 * @param setpoint
	 *            reference temperature in [1/10°C]
	 * @return never {@code null}
	 */
	public Device addDevice(String id, short setpoint) {
		assert id != null && !id.isEmpty();
		assert setpoint > 0;
		final Device device = createDevice(id, uri.toString(), setpoint, true, true, false); // Info + Status block
		devices.put(id, device);
		return device;
	}

	/**
	 * Adds a device and an adapter.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @param setpoint
	 *            reference temperature in [1/10°C]
	 * @param point
	 * @return never {@code null}
	 * @throws UnsupportedModelException
	 *             if the id does not represent a supported device model (see {@link DeviceModel})
	 */
	public DeviceTapPointAdapter addDevice(String id, short setpoint, TapPoint point) throws UnsupportedModelException {
		assert point != null;
		Device device = addDevice(id, setpoint);
		final DeviceTapPointAdapter adapter = new DeviceTapPointAdapter(point, device);
		adapters.put(id, adapter);
		return adapter;
	}

	@Override
	public Collection<Device> getDevices() {
		return Collections.unmodifiableCollection(devices.values());
	}

	public Device getDevice(String id) {
		return devices.get(id);
	}

	@Override
	public HomeServerResponse processStatusRequest() {
		return createResponse(true, true); // no devices
	}

	@Override
	public HomeServerResponse processDevicesRequest() {
		HomeServerResponse response = createResponse(false, false);
		response.devices = new ArrayList<Device>();
		response.total = devices.size();
		for (Device device : devices.values()) {
			Device newDevice = createDevice(device.id);
			newDevice.info = device.info; // attach only the Info block
			response.devices.add(newDevice);
		}
		return response;
	}

	@Override
	public HomeServerResponse processDeviceStatusRequest(String id) {
		final Device device = devices.get(id);
		if (device != null) {
			Device newDevice = createDevice(id);
			newDevice.status = device.status; // attach only the Status block
			HomeServerResponse response = createResponse(false, false);
			response.cached = false;
			response.total = 1;
			response.devices = new ArrayList<Device>();
			response.devices.add(newDevice);
			return response;
		}
		return null;
	}
	
	@Override
	public HomeServerResponse processDeviceSetpoint(String id, short setpoint) {
		final Device device = devices.get(id);
		if (device != null) {
			// change the "database":
			device.setSetpoint(setpoint);
			DeviceTapPointAdapter adapter = adapters.get(id);
			if (adapter != null) {
				adapter.updateTapPoint();
			}
			return processDeviceStatusRequest(id);
		}
		return null;
	}

	@Override
	public HomeServerResponse processSetScaldProtectionTemperature(String id, short temperature) {
		DeviceTapPointAdapter adapter = adapters.get(id);
		if (adapter != null) {
			adapter.getPoint().setScaldProtectionTemperature(HotWaterTemperature.fromInt(temperature / 10));
		}
		if (devices.containsKey(id)) {
			HomeServerResponse response = createResponse(false, false);
			response.response = new Response();
			response.response.data = Short.toString(temperature);
			return response;
		}
		return null;
	}

	@Override
	public HomeServerResponse processSetScaldProtectionFlag(String id, boolean on) {
		DeviceTapPointAdapter adapter = adapters.get(id);
		if (adapter != null && !on) {
			adapter.getPoint().setScaldProtectionTemperature(HotWaterTemperature.TEMP_MAX);
		}
		if (devices.containsKey(id)) {
			return createResponse(false, false);
		}
		return null;
	}

	@Override
	public void processUserFeedback(ElmUserFeedback feedback) {
		assert feedback != null;
		if (feedback.id != null) {
			// update the given tap point:
			assert feedback.deviceStatus != null;
			DeviceTapPointAdapter adapter = adapters.get(feedback.id);
			if (adapter != null) {
				if (feedback.deviceStatus != null) {
					adapter.getPoint().setStatus(feedback.deviceStatus);
				}
				if (feedback.expectedWaitingTimeMillis != null) {
					adapter.getPoint().setWaitingTimePercent(50); // TODO convert time to percent!
				}
			}
		} else {
			// update all tap points:
			assert feedback.schedulerStatus != null;
			for (DeviceTapPointAdapter adapter : adapters.values()) {
				adapter.getPoint().setStatus(feedback.schedulerStatus);
			}
		}
	}

	public static void print(Object obj) {
		Gson gson = new GsonBuilder().setFieldNamingStrategy(new HomeServerFieldNamingStrategy()).setPrettyPrinting().create();
		System.out.println("JSON: " + gson.toJson(obj));
	}

	protected HomeServerResponse createResponse(boolean putServices, boolean putServer) {
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

		if (putServer) {
			result.server = new Server();
			result.server.id = "D4CA6DB451EE";
			result.server.channel = 106;
			result.server.address = 178;
		}

		return result;
	}

	private Device createDevice(String id) {
		final Device d = new Device();
		d.id = id;
		d.rssi = -76;
		d.lqi = 15;
		d.connected = true;
		return d;
	}

	private Device createDevice(String id, String url, short setpoint, boolean info, boolean status, boolean logs) {
		final Device result = createDevice(id);

		if (info) {
			result.info = new Info();
			result.info.setpoint = setpoint;
			result.info.flags = 1; // heater OFF
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
			result.status.tIn = getIntakeWaterTemperature().getUnits();
			result.status.tOut = (short) (setpoint - 2);  // just to be more "real"
			result.status.tP1 = 350;
			result.status.tP2 = 380;
			result.status.tP3 = 420;
			result.status.tP4 = 480;
			result.status.flow = 25;
			result.status.power = 0;
			result.status.powerMax = (short) DeviceModel.SIM.getPowerMaxUnits();
			result.status.flags = 1; // heater OFF
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
