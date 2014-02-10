package elm.scheduler.model;

import static elm.scheduler.model.ModelTestUtil.createDevices;
import static elm.scheduler.model.ModelTestUtil.createHomeServer;
import static elm.scheduler.model.ModelTestUtil.getDeviceMap;
import static elm.scheduler.model.ModelTestUtil.sleep;
import static elm.scheduler.model.ModelTestUtil.toPowerUnits;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.model.Device;
import elm.scheduler.ElmStatus;
import elm.scheduler.HomeServerChangeListener;
import elm.scheduler.model.impl.DeviceInfoImpl;
import elm.scheduler.model.impl.HomeServerImpl;

public class HomeServerTest {

	static final int HS_ID = 1;
	static final int NUM_DEVICES = 2;
	static final int ACTUAL_POWER_WATT = 20_000;

	final Logger log = Logger.getLogger(getClass().getName());

	HomeServerImpl hs1;
	HomeServerChangeListener hsL1;

	@Before
	public void setup() {
		hs1 = (HomeServerImpl) createHomeServer(HS_ID, NUM_DEVICES);
		resetListener();
	}

	void resetListener() {
		hs1.removeChangeListener(hsL1);
		hsL1 = mock(HomeServerChangeListener.class);
		hs1.addChangeListener(hsL1);
	}

	@Test
	public void isAlive() {
		hs1.setPollTimeToleranceMillis(10);
		assertEquals(NUM_DEVICES, hs1.getDeviceInfos().size());

		assertFalse(hs1.isAlive()); // isAlive has side effects!
		assertFalse(hs1.isAlive());
		sleep(1);
		assertFalse(hs1.isAlive()); // assert isAlive did not have negative side effects
		sleep(1);
		hs1.updateLastHomeServerPollTime();
		assertTrue(hs1.isAlive());
		assertTrue(hs1.isAlive());
		sleep(5);
		assertTrue(hs1.isAlive());
		sleep(6);
		assertFalse(hs1.isAlive()); // no longer alive
	}

	@Test
	public void addRemoveDeviceInfoUpdates() {
		try {
			// add 2 more
			hs1.updateDeviceInfos(createDevices(HS_ID, 4, 0));
			assertEquals(4, hs1.getDeviceInfos().size());

			// remove 2
			List<Device> devices = createDevices(HS_ID, 4, 0);
			Device d0 = devices.get(0);
			Device d1 = devices.get(1);
			Device d2 = devices.get(2);
			Device d3 = devices.get(3);
			devices.remove(1); // remove #2
			devices.remove(1); // remove #3
			hs1.updateDeviceInfos(devices);
			assertEquals(2, hs1.getDeviceInfos().size());
			Map<String, DeviceInfo> map = getDeviceMap(hs1); // getDevicesInfos() is a Collection
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
		try {
			DeviceInfo[] deviceInfos = hs1.getDeviceInfos().toArray(new DeviceInfo[] {});
			DeviceInfo di1_2 = deviceInfos[1];
			
			List<Device> devices = createDevices(1, NUM_DEVICES, 0);
			Device d1_2 = devices.get(1);
			final short referenceTemperature = d1_2.status.setpoint;
			d1_2.status.power = toPowerUnits(20_000); // Turn tap 1-2 ON
			hs1.updateDeviceInfos(devices);
			assertNull(hs1.getPendingUpdates());
			// there should be no client invocations while there are no device updates:
			HomeServerInternalApiClient client = mock(HomeServerInternalApiClient.class);
			hs1.executePhysicalDeviceUpdates(client, log);
			verifyNoMoreInteractions(client);
			
			// Scheduler approves only LIMITED power:
			di1_2.updateMaximumPowerConsumption(ACTUAL_POWER_WATT, ElmStatus.OVERLOAD);
			assertEquals(1, hs1.getPendingUpdates().size());
			//
			hs1.fireDeviceChangesPending();
			verify(hsL1).deviceUpdatesPending(hs1, true); // listener was notified
			//
			short scaldProtectionTemperature = ((DeviceInfoImpl) di1_2).getScaldProtectionTemperature();
			when(client.setScaldProtectionTemperature(di1_2.getId(), scaldProtectionTemperature)).thenReturn(scaldProtectionTemperature);
			hs1.executePhysicalDeviceUpdates(client, log);
			assertNull(hs1.getPendingUpdates());
			verify(client).setScaldProtectionTemperature(di1_2.getId(), scaldProtectionTemperature);

			// next poll returns setpoint = scald-protection temperature:
			d1_2.status.setpoint = 265;
			hs1.updateDeviceInfos(devices);
			assertNull(hs1.getPendingUpdates());

			// Scheduler approves UNLIMITED power:
			di1_2.updateMaximumPowerConsumption(DeviceInfo.UNLIMITED_POWER, ElmStatus.OVERLOAD);
			assertEquals(1, hs1.getPendingUpdates().size());
			//
			hs1.executePhysicalDeviceUpdates(client, log);
			assertNull(hs1.getPendingUpdates());
			// ensure the original reference Temperature is restored:
			verify(client).clearScaldProtection(di1_2.getId(), referenceTemperature);
			
		} catch (ClientException | UnsupportedModelException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}
}
