package elm.scheduler.model;

import static elm.scheduler.model.ModelTestUtil.createDevices;
import static elm.scheduler.model.ModelTestUtil.createHomeServer;
import static elm.scheduler.model.ModelTestUtil.sleep;
import static elm.scheduler.model.ModelTestUtil.toPowerUnits;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.model.Device;
import elm.scheduler.HomeServerChangeListener;

public class HomeServerTest {

	private static final int HS_ID = 1;
	private static final int NUM_DEVICES = 2;

	HomeServer hs1;
	HomeServerChangeListener hsL1;

	@Before
	public void setup() {
		hs1 = createHomeServer(HS_ID, NUM_DEVICES);
		resetListener();
	}

	private void resetListener() {
		hs1.removeChangeListener(hsL1);
		hsL1 = mock(HomeServerChangeListener.class);
		hs1.addChangeListener(hsL1);
	}

	@Test
	public void isAlive() {
		assertEquals(NUM_DEVICES, hs1.getDevicesInfos().size());

		assertFalse(hs1.isAlive());
		sleep(1);
		assertFalse(hs1.isAlive()); // assert isAlive did not have negative side effects
		sleep(1);
		hs1.updateLastHomeServerPollTime();
		sleep(1);
		assertTrue(hs1.isAlive());
	}

	@Test
	public void deviceInfoUpdatesAddRemove() {
		try {
			// add 2 more
			hs1.updateDeviceInfos(createDevices(HS_ID, 4, 0));
			assertEquals(4, hs1.getDevicesInfos().size());

			// remove 2
			List<Device> devices = createDevices(HS_ID, 4, 0);
			Device d0 = devices.get(0);
			Device d1 = devices.get(1);
			Device d2 = devices.get(2);
			Device d3 = devices.get(3);
			devices.remove(1); // remove #2
			devices.remove(1); // remove #3
			hs1.updateDeviceInfos(devices);
			assertEquals(2, hs1.getDevicesInfos().size());
			Map<String, DeviceInfo> map = toMap(hs1.getDevicesInfos()); // getDevicesInfos() is a Collection
			assertTrue(map.containsKey(d0.id));
			assertFalse(map.containsKey(d1.id));
			assertFalse(map.containsKey(d2.id));
			assertTrue(map.containsKey(d3.id));

		} catch (UnsupportedModelException e) {
			assertTrue(false);
		}
	}

	@Test
	public void deviceInfoUpdates() {
		try {
			// Turn a tap on
			List<Device> devices = createDevices(HS_ID, NUM_DEVICES, 0);
			devices.get(1).status.power = toPowerUnits(10_000);
			hs1.updateDeviceInfos(devices);
			verify(hsL1).deviceInfosUpdated(hs1, true);

			// Turn a tap off
			resetListener();
			devices = createDevices(HS_ID, NUM_DEVICES, 0);
			hs1.updateDeviceInfos(devices);
			verify(hsL1).deviceInfosUpdated(hs1, false);

			// Turn nothing on or off
			resetListener();
			hs1.updateDeviceInfos(devices);
			verifyNoMoreInteractions(hsL1);

		} catch (UnsupportedModelException e) {
			assertTrue(false);
		}
	}

	@Test
	public void deviceUpdates() {
		AbstractDeviceUpdate upd1 = mock(AbstractDeviceUpdate.class);
		when(upd1.isUrgent()).thenReturn(true);
		AbstractDeviceUpdate upd2 = mock(AbstractDeviceUpdate.class);
		when(upd2.isUrgent()).thenReturn(false);
		hs1.putDeviceUpdate(upd1);
		hs1.putDeviceUpdate(upd2);
		hs1.fireDeviceChangesPending();
		verify(hsL1).deviceUpdatesPending(hs1, true);

		HomeServerInternalApiClient client = mock(HomeServerInternalApiClient.class);
		hs1.executeDeviceUpdates(client);
		try {
			verify(upd1).run(client);
			verify(upd2).run(client);
		} catch (ClientException e) {
			assertTrue(false);
		}
	}

	private Map<String, DeviceInfo> toMap(Collection<DeviceInfo> collection) {
		Map<String, DeviceInfo> map = new HashMap<String, DeviceInfo>();
		for (DeviceInfo obj : collection) {
			map.put(obj.getId(), obj);
		}
		return map;
	}
}
