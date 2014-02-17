package elm.scheduler.model.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.hs.api.model.Error;
import elm.hs.api.model.Info;
import elm.hs.api.model.Status;
import elm.scheduler.model.AsynchRemoteDeviceUpdate;
import elm.scheduler.model.DeviceManager;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.UnsupportedModelException;
import elm.scheduler.model.impl.HomeServerImpl;

public class ModelTestUtil {
	private static final String SIM_TYPE_ID = "D012"; // Model SIM
	private static final short SIM_POWER_MAX_UNITS = 180; // Model SIM
	private static final int SIM_POWER_MAX_WATT = DeviceModel.SIM.getPowerMaxWatt(); // Model SIM

	public static HomeServer createHomeServer(int id, int devices) {
		HomeServer hs = new HomeServerImpl(URI.create("http://hs" + id), "pw", "hs" + id);
		try {
			hs.updateDeviceManagers(ModelTestUtil.createDevicesWithInfo(id, devices));
			hs.updateDeviceManagers(ModelTestUtil.createDevicesWithStatus(id, devices, 0));
		} catch (UnsupportedModelException e) {
			throw new IllegalArgumentException(e);
		}
		return hs;
	}

	public static Device createDeviceWithInfo(int homeServerId, int deviceId) {
		assert homeServerId >= 0 && homeServerId < 100;
		assert deviceId > 0 && deviceId < 100;
		Device d = new Device();
		// id = <typeId>_<homeServerId>-<deviceId>
		StringBuilder b = new StringBuilder(SIM_TYPE_ID);
		b.append('_');
		if (homeServerId < 10) b.append('_');
		if (deviceId < 10) b.append('_');
		b.append(homeServerId);
		b.append('-');
		b.append(deviceId);
		d.id = b.toString();
		d.connected = true;

		d.info = new Info();
		d.info.flags = 1; // = heater off
		d.info.error = Error.OK.getCode();
		return d;
	}

	/**
	 * Returns a number of {@link Device}s with just an {@link Info} block.
	 * 
	 * @param homeServerId
	 *            {@code >= 0}
	 * @param n
	 *            first deviceID is {@code 1}; a value of {@code 0} yields an empty list
	 * @return never {@code null}
	 */
	public static List<Device> createDevicesWithInfo(int homeServerId, int n) {
		assert n >= 0 && n < 100;
		List<Device> result = new ArrayList<Device>();
		if (n > 0) {
			for (int i = 1; i <= n; i++) {
				result.add(createDeviceWithInfo(homeServerId, i));
			}
		}
		return result;
	}

	/**
	 * 
	 * @param homeServerNr
	 * @param deviceNr
	 * @param powerWatt currently used power [W]
	 * @return
	 */
	public static Device createDeviceWithStatus(int homeServerNr, int deviceNr, int powerWatt) {
		assert deviceNr > 0 && deviceNr < 100;
		Device d = createDeviceWithInfo(homeServerNr, deviceNr);
		d.status = new Status();
		d.status.setpoint = 380; // 38°C
		d.status.tIn = 100; // 10°C
		d.status.tOut = (short) (d.status.setpoint - 2);
		d.status.powerMax = SIM_POWER_MAX_UNITS;
		d.status.power = toPowerUnits(powerWatt);
		d.status.error = d.info.error;
		d.status.flags = (short) (powerWatt > 0 ? 0 : 1); // heater on: flags == 0
		// delete Info block:
		d.info = null;
		return d;
	}

	/**
	 * Returns a number of {@link Device}s with just a {@link Status} block.
	 * 
	 * @param homeServerId
	 *            {@code >= 0}
	 * @param n
	 *            first deviceID is {@code 1}; a value of {@code 0} yields an empty list
	 * @return never {@code null}
	 */
	public static List<Device> createDevicesWithStatus(int homeServerId, int n, int powerWatt) {
		assert homeServerId >= 0 && homeServerId < 100;
		assert n >= 0 && n < 100;
		List<Device> result = new ArrayList<Device>();
		if (n > 0) {
			for (int i = 1; i <= n; i++) {
				result.add(createDeviceWithStatus(homeServerId, i, powerWatt));
			}
		}
		return result;
	}

	public static short toPowerUnits(int powerWatt) {
		return (short) (SIM_POWER_MAX_UNITS * powerWatt / SIM_POWER_MAX_WATT);
	}

	public static int toPowerWatt(short powerUnits) {
		return (short) (SIM_POWER_MAX_WATT * powerUnits / SIM_POWER_MAX_UNITS);
	}

	public static int round(int powerWatt) {
		return toPowerWatt(toPowerUnits(powerWatt));
	}

	public static void sleep(int milliseconds) {
		Object obj = new Object();
		synchronized (obj) {
			try {
				obj.wait(milliseconds);
			} catch (InterruptedException e) {
				// do nothing
			}
		}

	}

	public static List<String> getDeviceIds(HomeServer server) {
		assert server != null;
		List<String> list = new ArrayList<String>();
		for (DeviceManager obj : server.getDeviceManagers()) {
			list.add(obj.getId());
		}
		Collections.sort(list);
		return list;
	}

	public static Map<String, DeviceManager> getDeviceMap(HomeServer server) {
		assert server != null;
		Map<String, DeviceManager> map = new HashMap<String, DeviceManager>();
		for (DeviceManager obj : server.getDeviceManagers()) {
			map.put(obj.getId(), obj);
		}
		return map;
	}

	public static AsynchRemoteDeviceUpdate getDeviceUpdate(HomeServer server, Device device) {
		assert server != null;
		assert device != null;
		for (AsynchRemoteDeviceUpdate upd : ((HomeServerImpl) server).getPendingUpdates()) {
			if (upd.getDevice().getId().equals(device.id)) {
				return upd;
			}
		}
		return null;
	}

	public static void checkDeviceUpdate(HomeServer server, Device device, int expectedLimitWatt) {
		final DeviceManager deviceManager = server.getDeviceManager(device.id);
		assertEquals(expectedLimitWatt == DeviceManager.UNLIMITED_POWER ? deviceManager.getDeviceModel().getPowerMaxWatt() : expectedLimitWatt,
				deviceManager.getApprovedPowerWatt());
		final AsynchRemoteDeviceUpdate deviceUpdate = getDeviceUpdate(server, device);
		assertNotNull(deviceUpdate);
	}
}
