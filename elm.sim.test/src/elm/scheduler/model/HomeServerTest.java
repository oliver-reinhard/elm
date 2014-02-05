package elm.scheduler.model;

import static elm.scheduler.model.ModelTestUtil.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;

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
			Device d3 = devices.get(3);
			devices.remove(1); // remove #2
			devices.remove(1);// remove #3
			hs1.updateDeviceInfos(devices);
			assertEquals(2, hs1.getDevicesInfos().size());
			assertTrue(hs1.getDevicesInfos().contains(d0));
			assertTrue(hs1.getDevicesInfos().contains(d3));

		} catch (UnsupportedModelException e) {
			assertTrue(false);
		}
	}

	@Test
	public void deviceInfoUpdates() {
		List<Device> devices = createDevices(HS_ID, NUM_DEVICES, 0);
		devices.get(1).status.power = toPowerUnits(10_000);
		try {
			hs1.updateDeviceInfos(devices);

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
}
