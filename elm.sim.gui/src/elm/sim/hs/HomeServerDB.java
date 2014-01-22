package elm.sim.hs;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.sim.hs.model.Device;
import elm.sim.hs.model.HomeServerFieldNamingStrategy;
import elm.sim.hs.model.Info;
import elm.sim.hs.model.Log;
import elm.sim.hs.model.Server;
import elm.sim.hs.model.ServerStatus;
import elm.sim.hs.model.Service;
import elm.sim.hs.model.Setup;
import elm.sim.hs.model.Status;

public class HomeServerDB {

	private ServerStatus status;

	private final List<Device> devices = new ArrayList<Device>();

	/**
	 * Returns the general Home Server status.
	 * 
	 * @return {@code null before initialized}
	 */
	public ServerStatus getStatus() {
		return status;
	}

	public List<Device> getDevices() {
		return devices;
	}

	public static void main(String[] args) {

		HomeServerDB db = new HomeServerDB();

		Device dev = addMockData(db);

		Gson gson = new GsonBuilder().setFieldNamingStrategy(new HomeServerFieldNamingStrategy()).setPrettyPrinting().create();
		System.out.println("JSON: " + gson.toJson(dev));
	}

	public static Device addMockData(HomeServerDB db) {
		final ServerStatus status = new ServerStatus();
		db.status = status;
		status.version = "1.0";
		status.total = 6;
		status.cached = true;
		status.success = true;
		status.error = 0;
		status.time = 1390317089;
		status.services = new ArrayList<Service>();
		
		// Services
		Service s = new Service();
		s.deviceList = Service.DEVICES_PATH;
		status.services.add(s);
		
		s = new Service();
		s.deviceStatus = Service.STATUS_PATH;
		status.services.add(s);
		
		s = new Service();
		s.deviceSetpoint = Service.SETPOINT_PATH;
		status.services.add(s);
		
		s = new Service();
		s.deviceLogs = Service.LOGS_PATH;
		status.services.add(s);
		
		s = new Service();
		s.fileList = Service.FILES_PATH;
		status.services.add(s);
		
		s = new Service();
		s.timerList = Service.TIMERS_PATH;
		status.services.add(s);

		status.server = new Server();
		status.server.id = "D4CA6DB451EE";
		status.server.channel = 106;
		status.server.address = 178;

		
		// Devices
		Device dev = new Device();
		dev.id = "A001010214";
		dev.rssi = -76;
		dev.lqi = 15;
		dev.connected = true;

		db.getDevices().add(dev);

		dev.info = new Info();
		dev.info.setpoint = 380;
		dev.info.flags = 0;
		dev.info.error = 0;
		dev.info.access = 255;
		dev.info.activity = 1355266800;
		dev.info.url = "https://chs.local:443";
		dev.info.serverCh = 106;
		dev.info.serverAddr = 100;

		dev.status = new Status();
		dev.status.setpoint = 380;
		dev.status.tIn = 115;
		dev.status.tOut = 379;
		dev.status.tP1 = 350;
		dev.status.tP2 = 380;
		dev.status.tP3 = 420;
		dev.status.tP4 = 480;
		dev.status.flow = 25;
		dev.status.power = 0;
		dev.status.powerMax = 180;
		dev.status.flags = 0;
		dev.status.error = 0;

		dev.setup = new Setup();
		dev.setup.swVersion = "1.4.1";
		dev.setup.serialDevice = "<serial>";
		dev.setup.serialPowerUnit = "<serial>";
		dev.setup.flowMax = 254;
		dev.setup.loadShedding = 0;
		dev.setup.scaldProtection = 65535;
		dev.setup.fcpAddr = 80;
		dev.setup.powerCosts = 0;
		dev.setup.powerMax = 140;
		dev.setup.calValue = 0;
		dev.setup.timerPowerOn = 300;
		dev.setup.timerLifetime = 172800;
		dev.setup.timerStandby = 2400;
		dev.setup.totalPowerConsumption = 0;
		dev.setup.totalWaterConsumption = 0;

		dev.logs = new ArrayList<Log>();
		Log log1 = new Log();
		log1.id = 1;
		log1.time = 1355266800;
		log1.length = 10;
		log1.power = 6;
		log1.water = 42;
		log1.cid = 2;
		dev.logs.add(log1);

		Log log2 = new Log();
		log2.id = 2;
		log2.time = 1355269200;
		log2.length = 10;
		log2.power = 6;
		log2.water = 38;
		log2.cid = 2;
		dev.logs.add(log2);
		return dev;
	}

}
