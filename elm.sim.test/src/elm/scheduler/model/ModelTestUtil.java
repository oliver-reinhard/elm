package elm.scheduler.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import elm.hs.api.model.Device;
import elm.hs.api.model.Status;
import elm.scheduler.model.impl.HomeServerImpl;

public class ModelTestUtil {
	private static final String DSX_TYPE_ID = "2016"; // Model DSX
	private static final short DSX_POWER_MAX_UNITS = 180; // Model DSX
	private static final int DSX_POWER_MAX_WATT = 27_000; // Model DSX

	public static HomeServer createHomeServer(int id, int devices) {
		HomeServer hs = new HomeServerImpl(URI.create("http://hs" + id), "pw", "hs" + id);
		List<Device> deviceList = new ArrayList<Device>();
		for (int i = 1; i <= devices; i++) {
			deviceList.add(ModelTestUtil.createDevice(id, i, 0));
		}
		try {
			hs.updateDeviceInfos(deviceList);
		} catch (UnsupportedModelException e) {
			throw new IllegalArgumentException(e);
		}
		return hs;
	}

	public static Device createDevice(int homeServerId, int deviceId, int powerWatt) {
		assert homeServerId >= 0 && homeServerId < 100;
		assert deviceId > 0 && deviceId < 100;
		Device d = new Device();
		// id = <typeId>_<homeServerId>-<deviceId>
		StringBuilder b = new StringBuilder(DSX_TYPE_ID);
		b.append('_');
		if (homeServerId < 10) b.append('_');
		if (deviceId < 10) b.append('_');
		b.append(homeServerId);
		b.append('-');
		b.append(deviceId);
		d.id = b.toString();
		d.status = new Status();
		d.connected = true;
		d.status.setpoint = 380;  // 38°C
		d.status.powerMax = DSX_POWER_MAX_UNITS;
		d.status.tIn = 100;  // 10°C
		d.status.power = toPowerUnits(powerWatt);
		return d;
	}

	public static List<Device> createDevices(int homeServerId, int n, int powerWatt) {
		assert homeServerId >= 0 && homeServerId < 100;
		assert n > 0 && n < 100;
		List<Device> result = new ArrayList<Device>();
		for (int i = 1; i <= n; i++) {
			result.add(createDevice(homeServerId, i, powerWatt));
		}
		return result;
	}

	public static short toPowerUnits(int powerWatt) {
		return (short) (DSX_POWER_MAX_UNITS * powerWatt / DSX_POWER_MAX_WATT);
	}

	public static int toPowerWatt(short powerUnits) {
		return (short) (DSX_POWER_MAX_WATT * powerUnits / DSX_POWER_MAX_UNITS);
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
		for (DeviceInfo obj : server.getDeviceInfos()) {
			list.add(obj.getId());
		}
		Collections.sort(list);
		return list;
	}

	public static Map<String, DeviceInfo> getDeviceMap(HomeServer server) {
		assert server != null;
		Map<String, DeviceInfo> map = new HashMap<String, DeviceInfo>();
		for (DeviceInfo obj : server.getDeviceInfos()) {
			map.put(obj.getId(), obj);
		}
		return map;
	}

	public static AsynchronousDeviceUpdate getDeviceUpdate(HomeServer server, Device device) {
		assert server != null;
		assert device != null;
		for (AsynchronousDeviceUpdate upd : ((HomeServerImpl) server).getPendingUpdates()) {
			if (upd.getDevice().getId().equals(device.id)) {
				return upd;
			}
		}
		return null;
	}

	public static void checkDeviceUpdate(HomeServer server, Device device, int expectedLimitWatt) {
		final DeviceInfo deviceInfo = server.getDeviceInfo(device.id);
		assertEquals(expectedLimitWatt == DeviceInfo.UNLIMITED_POWER ? deviceInfo.getDeviceModel().getPowerMaxWatt() : expectedLimitWatt,
				deviceInfo.getApprovedPowerWatt());
		final AsynchronousDeviceUpdate deviceUpdate = getDeviceUpdate(server, device);
		assertNotNull(deviceUpdate);
	}
}
