package elm.scheduler.model.impl;

import static elm.scheduler.model.impl.ModelTestUtil.FLOW_OFF;
import static elm.scheduler.model.impl.ModelTestUtil.FLOW_ON;
import static elm.scheduler.model.impl.ModelTestUtil.checkDeviceUpdatesSize;
import static elm.scheduler.model.impl.ModelTestUtil.createDeviceWithStatus;
import static elm.scheduler.model.impl.ModelTestUtil.createDevicesWithStatus;
import static elm.scheduler.model.impl.ModelTestUtil.createHomeServer;
import static elm.scheduler.model.impl.ModelTestUtil.getDeviceMap;
import static elm.scheduler.model.impl.ModelTestUtil.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import elm.hs.api.Device;
import elm.hs.api.ElmStatus;
import elm.hs.api.ElmUserFeedbackService;
import elm.hs.api.HomeServerInternalService;
import elm.scheduler.ElmUserFeedbackManager;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.HomeServerChangeListener;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.util.ClientException;

public class HomeServerTest {

	static final int HS_ID = 1;
	static final int NUM_DEVICES = 2;
	static final int ACTUAL_POWER_WATT = 20_000;
	static final int EXPECTED_WAITING_TIME = 5_000;

	final Logger log = Logger.getLogger(getClass().getName());

	ElmUserFeedbackManager feedbackManager;
	ElmUserFeedbackService feedbackClient;
	HomeServerImpl hs1;
	HomeServerChangeListener hsL1;

	@Before
	public void setup() {
		feedbackManager = mock(ElmUserFeedbackManager.class);
		feedbackClient = mock(ElmUserFeedbackService.class);
		hs1 = (HomeServerImpl) createHomeServer(HS_ID, NUM_DEVICES, feedbackManager, feedbackClient);
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
		assertEquals(NUM_DEVICES, hs1.getDeviceControllers().size());

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
	public void addRemoveDeviceManagerUpdates() {
		try {
			// add 2 more
			hs1.updateDeviceControllers(createDevicesWithStatus(HS_ID, 4, 0, FLOW_OFF));
			assertEquals(4, hs1.getDeviceControllers().size());

			// remove 2
			List<Device> devices = createDevicesWithStatus(HS_ID, 4, 0, FLOW_OFF);
			Device d0 = devices.get(0);
			Device d1 = devices.get(1);
			Device d2 = devices.get(2);
			Device d3 = devices.get(3);
			devices.remove(1); // remove #2
			devices.remove(1); // remove #3
			hs1.updateDeviceControllers(devices);
			assertEquals(2, hs1.getDeviceControllers().size());
			Map<String, DeviceController> map = getDeviceMap(hs1); // getDevicesInfos() is a Collection
			assertTrue(map.containsKey(d0.id));
			assertFalse(map.containsKey(d1.id));
			assertFalse(map.containsKey(d2.id));
			assertTrue(map.containsKey(d3.id));

		} catch (UnsupportedDeviceModelException e) {
			fail(e.toString());
			e.printStackTrace();
		}
	}

	@Test
	public void devicesControllersUpdated() {
		try {
			// Turn a tap ON
			List<Device> devices = createDevicesWithStatus(HS_ID, NUM_DEVICES, 0, FLOW_OFF);
			devices.set(1, createDeviceWithStatus(1, 2, 10_000, FLOW_ON));
			hs1.updateDeviceControllers(devices);
			verify(hsL1).devicesControllersUpdated(hs1, true);

			// Turn a tap OFF
			resetListener();
			devices = createDevicesWithStatus(HS_ID, NUM_DEVICES, 0, FLOW_OFF);
			hs1.updateDeviceControllers(devices);
			verify(hsL1).devicesControllersUpdated(hs1, true);

			// Turn nothing ON or OFF
			resetListener();
			hs1.updateDeviceControllers(devices);
			verifyNoMoreInteractions(hsL1);

		} catch (UnsupportedDeviceModelException e) {
			fail(e.toString());
			e.printStackTrace();
		}
	}

	@Test
	public void deviceUpdates() {
		try {
			DeviceController[] deviceManagers = hs1.getDeviceControllers().toArray(new DeviceController[] {});
			DeviceController di1_2 = deviceManagers[1];

			List<Device> devices = createDevicesWithStatus(1, NUM_DEVICES, 0, FLOW_OFF);
			Device d1_1 = devices.get(0);
			devices.set(1, createDeviceWithStatus(1, 2, 20_000, FLOW_ON)); // Turn tap 1-2 ON
			Device d1_2 = devices.get(1);
			final short referenceTemperature = d1_2.status.setpoint;
			hs1.updateDeviceControllers(devices);
			// setpoint was accepted as user temperature:
			assertEquals(referenceTemperature, hs1.getDeviceController(d1_2.id).getUserDemandTemperatureUnits());
			checkDeviceUpdatesSize(hs1, 2); // => clear-scald protection flag
			//
			HomeServerInternalService client = mock(HomeServerInternalService.class);
			hs1.executeRemoteDeviceUpdates(client, log);
			verify(client).clearScaldProtection(d1_1.id, (int) ModelTestUtil.INITIAL_INFO_SETPOINT); // initial value
			verify(client).clearScaldProtection(d1_2.id, (int) ModelTestUtil.INITIAL_INFO_SETPOINT);

			// Scheduler approves only LIMITED power:
			di1_2.updateMaximumPowerConsumption(ElmStatus.OVERLOAD, ACTUAL_POWER_WATT / 2);
			di1_2.updateUserFeedback(ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
			checkDeviceUpdatesSize(hs1, 1);
			//
			hs1.fireDeviceUpdatesPending();
			verify(hsL1).deviceUpdatesPending(hs1); // listener was notified
			//
			short scaldProtectionTemperature = ((DeviceControllerImpl) di1_2).getScaldProtectionTemperatureUnits();
			when(client.setScaldProtectionTemperature(di1_2.getId(), scaldProtectionTemperature)).thenReturn(scaldProtectionTemperature);
			hs1.executeRemoteDeviceUpdates(client, log);
			assertNull(hs1.getPendingUpdates());
			verify(client).setScaldProtectionTemperature(di1_2.getId(), scaldProtectionTemperature);

			// next poll returns reduced power and setpoint == scald-protection temperature:
			devices.set(1, createDeviceWithStatus(1, 2, 10_000, FLOW_ON)); // Turn tap 1-2 ON
			d1_2 = devices.get(1);
			hs1.updateDeviceControllers(devices);
			assertNull(hs1.getPendingUpdates());
			// original user temperature remains unchanged:
			assertEquals(referenceTemperature, hs1.getDeviceController(d1_2.id).getUserDemandTemperatureUnits());

			// Scheduler approves UNLIMITED power:
			di1_2.updateMaximumPowerConsumption(ElmStatus.OVERLOAD, DeviceController.UNLIMITED_POWER);
			di1_2.updateUserFeedback(ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
			checkDeviceUpdatesSize(hs1, 1);
			//
			hs1.executeRemoteDeviceUpdates(client, log);
			assertNull(hs1.getPendingUpdates());
			// ensure the original reference Temperature is restored:
			verify(client).clearScaldProtection(di1_2.getId(), new Integer(referenceTemperature));

		} catch (ClientException | UnsupportedDeviceModelException e) {
			fail(e.toString());
			e.printStackTrace();
		}
	}
}
