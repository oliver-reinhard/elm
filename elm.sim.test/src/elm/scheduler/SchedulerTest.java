package elm.scheduler;

import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceController.DeviceStatus.READY;
import static elm.scheduler.model.impl.ModelTestUtil.checkDeviceUpdate;
import static elm.scheduler.model.impl.ModelTestUtil.checkDeviceUpdatesSize;
import static elm.scheduler.model.impl.ModelTestUtil.createDevicesWithStatus;
import static elm.scheduler.model.impl.ModelTestUtil.createHomeServer;
import static elm.scheduler.model.impl.ModelTestUtil.sleep;
import static elm.scheduler.model.impl.ModelTestUtil.toPowerUnits;
import static elm.ui.api.ElmStatus.ERROR;
import static elm.ui.api.ElmStatus.OFF;
import static elm.ui.api.ElmStatus.ON;
import static elm.ui.api.ElmStatus.OVERLOAD;
import static elm.ui.api.ElmStatus.SATURATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import elm.hs.api.model.Device;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.HomeServerChangeListener;
import elm.scheduler.model.RemoteDeviceUpdateClient;
import elm.scheduler.model.UnsupportedModelException;

@RunWith(MockitoJUnitRunner.class)
public class SchedulerTest {

	static final int NUM_HOME_SERVERS = 3;
	static final int NUM_DEVICES = 2;

	final Logger log = Logger.getLogger(getClass().getName());

	HomeServer hs1;
	HomeServer hs2;
	HomeServer hs3;
	HomeServerChangeListener hsL1;
	HomeServerChangeListener hsL2;
	HomeServerChangeListener hsL3;
	Scheduler scheduler;
	SchedulerChangeListener statusL;

	@Before
	public void setup() {
		hs1 = createHomeServer(1, NUM_DEVICES);
		hsL1 = mock(HomeServerChangeListener.class);
		hs1.addChangeListener(hsL1);

		hs2 = createHomeServer(2, NUM_DEVICES);
		hsL2 = mock(HomeServerChangeListener.class);
		hs2.addChangeListener(hsL2);

		hs3 = createHomeServer(3, NUM_DEVICES);
		hsL3 = mock(HomeServerChangeListener.class);
		hs3.addChangeListener(hsL3);

		scheduler = new Scheduler(50_000, 30_000);
		statusL = mock(SchedulerChangeListener.class);
		scheduler.addChangeListener(statusL);
	}

	@Test
	public void isAlive() {
		scheduler.addHomeServer(hs1);
		assertEquals(OFF, scheduler.getStatus());
		assertFalse(hs1.isAlive()); // hs1 => is not alive!
		scheduler.runOnce();
		assertEquals(ERROR, scheduler.getStatus());

		sleep(1);
		hs1.updateLastHomeServerPollTime();
		assertTrue(hs1.isAlive());
		sleep(1);
		hs1.updateLastHomeServerPollTime();
		scheduler.runOnce();
		assertEquals(ON, scheduler.getStatus());
	}

	/**
	 * Runs a full scheduling state cycle; checks states but ignores device updates.
	 */
	@Test
	public void scheduling_IgnoreDeviceUpdates() {
		try {
			scheduler.setIsAliveCheckDisabled(true); // enable debugger

			scheduler.addHomeServer(hs1);
			scheduler.addHomeServer(hs2);
			scheduler.addHomeServer(hs3);

			hs1.updateLastHomeServerPollTime(); // otherwise HomeServer is not alive
			hs2.updateLastHomeServerPollTime();
			hs3.updateLastHomeServerPollTime();

			assertEquals(OFF, scheduler.getStatus());

			int runCount = scheduler.getSchdedulingRunCount();
			//
			// start a scheduler run manually:
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(runCount + 1, scheduler.getSchdedulingRunCount());

			// ON --> ON
			List<Device> hs1_Devices = createDevicesWithStatus(1, NUM_DEVICES, 0);
			Device d1_1 = hs1_Devices.get(0);
			Device d1_2 = hs1_Devices.get(1);
			d1_2.status.power = toPowerUnits(20_000); // Turn tap 1-2 ON
			//
			// the following command would normally trigger a run but we have not started the scheduler => no run
			hs1.updateDeviceControllers(hs1_Devices);
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_STARTED, hs1.getDeviceController(d1_2.id).getStatus());
			runCount = scheduler.getSchdedulingRunCount();
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(runCount + 1, scheduler.getSchdedulingRunCount());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceController(d1_2.id).getStatus());
			//
			// Add more devices:
			List<Device> hs2_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0);
			Device d2_1 = hs2_Devices.get(0);
			Device d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			//
			List<Device> hs3_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0);
			Device d3_1 = hs3_Devices.get(0);
			Device d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceController(d1_2.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_2.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_2.id).getStatus());
			
			// ON --> SATURATION
			d2_2.status.power = toPowerUnits(20_000); // Turn tap 2-2 ON
			hs2.updateDeviceControllers(hs2_Devices);
			assertEquals(CONSUMPTION_STARTED, hs2.getDeviceController(d2_2.id).getStatus());
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceController(d1_2.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs2.getDeviceController(d2_2.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_2.id).getStatus());

			// SATURATION --> OVERLOAD
			d3_2.status.power = toPowerUnits(20_000); // Turn tap 3-2 ON
			hs3.updateDeviceControllers(hs3_Devices);
			assertEquals(CONSUMPTION_STARTED, hs3.getDeviceController(d3_2.id).getStatus()); // tap 3-2 is CONSUMING (new)
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(OVERLOAD, scheduler.getStatus());
			assertTrue(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceController(d1_2.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs2.getDeviceController(d2_2.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(CONSUMPTION_DENIED, hs3.getDeviceController(d3_2.id).getStatus()); 

			// OVERLOAD --> SATURATION
			d1_2.status.power = toPowerUnits(10_000); // Reduce tap 1-2 power => tap 3-2 can run as well
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceController(d1_2.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs2.getDeviceController(d2_2.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs3.getDeviceController(d3_2.id).getStatus()); // tap 3-2 CONSUMING now

			// SATURATION --> ON
			d2_2.status.power = toPowerUnits(0); // Turn tap 2-2 OFF
			hs2.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceController(d1_2.id).getStatus()); // tap 1-2 still consuming
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_2.id).getStatus()); // tap 2-2 is OFF
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs3.getDeviceController(d3_2.id).getStatus()); // tap 3-2 still consuming

			// ON --> ON
			d1_2.status.power = toPowerUnits(0); // Turn tap 1-2 OFF
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(READY, hs1.getDeviceController(d1_2.id).getStatus()); // tap 1-2 is OFF
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_2.id).getStatus()); // tap 2-2 is OFF
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs3.getDeviceController(d3_2.id).getStatus()); // tap 3-2 still consuming

			// ON --> ON
			d3_2.status.power = toPowerUnits(0); // Turn tap 3-2 OFF
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(READY, hs1.getDeviceController(d1_2.id).getStatus()); // tap 1-2 is OFF
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_2.id).getStatus()); // tap 2-2 is OFF
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_2.id).getStatus()); // tap 3-2 is OFF

		} catch (UnsupportedModelException e) {
			fail(e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * Runs a limited On-On scheduling state cycle; ignores most device states but checks all device updates.
	 */
	@Test
	public void scheduling_IgnoreDeviceStates() {
		try {
			scheduler.setIsAliveCheckDisabled(true); // enable debugger

			checkDeviceUpdatesSize(hs1, 0);
			scheduler.addHomeServer(hs1);
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, OFF, 0);
			//
			// home server 1: process device updates => "execute" (and clear) the device updates
			RemoteDeviceUpdateClient client = mock(RemoteDeviceUpdateClient.class);
			hs1.executeRemoteDeviceUpdates(client, log); // clear

			scheduler.addHomeServer(hs2);
			checkDeviceUpdatesSize(hs1, 0);  // no influence on hs1
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, OFF, 0);
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			

			scheduler.addHomeServer(hs3);
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			hs1.updateLastHomeServerPollTime(); // otherwise HomeServer is not alive
			hs2.updateLastHomeServerPollTime();
			hs3.updateLastHomeServerPollTime();
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());

			// home server 1.3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, ON, 0);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, ON, 0);
			checkDeviceUpdatesSize(hs3, 1);
			checkDeviceUpdate(hs3, ON, 0);
			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			// ON --> ON
			List<Device> hs1_Devices = createDevicesWithStatus(1, NUM_DEVICES, 0);
			Device d1_1 = hs1_Devices.get(0);
			Device d1_2 = hs1_Devices.get(1);
			hs1.updateDeviceControllers(hs1_Devices);

			List<Device> hs2_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0);
			Device d2_1 = hs2_Devices.get(0);
			Device d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			
			List<Device> hs3_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0);
			Device d3_1 = hs3_Devices.get(0);
			Device d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			// no device updates anywhere
			checkDeviceUpdatesSize(hs1, 0);
			checkDeviceUpdatesSize(hs2, 0);
			checkDeviceUpdatesSize(hs3, 0);

			// ON --> ON
			d1_2.status.power = toPowerUnits(20_000); // Turn tap 1-2 ON
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_2, ON, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs2, 0);
			checkDeviceUpdatesSize(hs3, 0);
			//
			hs1.executeRemoteDeviceUpdates(client, log); // clear

			// ON --> ON
			d2_2.status.power = toPowerUnits(5_000); // Turn tap 2-2 ON (low)
			hs2.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 0);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, d2_2, ON, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs3, 0);
			//
			hs2.executeRemoteDeviceUpdates(client, log); // clear

			// ON --> SATURATION
			d2_2.status.power = toPowerUnits(20_000); // Turn tap 2-2 ON (high)
			hs2.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, SATURATION, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, d2_1, SATURATION, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs3, 2);
			checkDeviceUpdate(hs3, d3_1, SATURATION, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs3, d3_2, SATURATION, DeviceController.UNLIMITED_POWER);
			//
			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			// SATURATION --> SATURATION
			d1_1.status.power = toPowerUnits(5_000); // Turn tap 1-1 ON (low)
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, ON, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs2, 0);
			checkDeviceUpdatesSize(hs3, 0);
			//
			hs1.executeRemoteDeviceUpdates(client, log); // clear

			// SATURATION --> SATURATION
			d1_1.status.power = toPowerUnits(0); // Turn tap 1-1 OFF
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, SATURATION, DeviceController.UNLIMITED_POWER); // CONSUMPTION_ENDED confirmation
			checkDeviceUpdatesSize(hs2, 0);
			checkDeviceUpdatesSize(hs3, 0);
			//
			hs1.executeRemoteDeviceUpdates(client, log); // clear

			// SATURATION --> OVERLOAD
			d3_2.status.power = toPowerUnits(15_000); // Turn tap 3-2 ON => will not be allowed
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(OVERLOAD, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, OVERLOAD, DeviceController.NO_POWER);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, d2_1, OVERLOAD, DeviceController.NO_POWER);
			checkDeviceUpdatesSize(hs3, 2);
			checkDeviceUpdate(hs3, d3_1, OVERLOAD, DeviceController.NO_POWER);
			checkDeviceUpdate(hs3, d3_2, OVERLOAD, DeviceController.NO_POWER);
			
			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			// OVERLOAD --> OVERLOAD
			d3_2.status.power = toPowerUnits(11_000); // Turn tap 3-2 DOWN
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(OVERLOAD, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 0);
			checkDeviceUpdatesSize(hs2, 0);
			checkDeviceUpdatesSize(hs3, 0);

			// OVERLOAD --> SATURATION
			d3_2.status.power = toPowerUnits(4000); // Turn tap 3-2 DOWN => will now be allowed
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, SATURATION, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, d2_1, SATURATION, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs3, 2);
			checkDeviceUpdate(hs3, d3_1, SATURATION, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs3, d3_2, ON, DeviceController.UNLIMITED_POWER); 
			
			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear
			

			// SATURATION --> ON
			d2_2.status.power = toPowerUnits(5_000); // Turn tap 2-2 ON (low)
			hs2.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, ON, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, d2_1, ON, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs3, 1);
			checkDeviceUpdate(hs3, d3_1, ON, DeviceController.UNLIMITED_POWER);
			//
			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			// ON --> ON
			d2_2.status.power = toPowerUnits(0); // Turn tap 2-2 OFF
			hs2.updateDeviceControllers(hs2_Devices);
			d3_2.status.power = toPowerUnits(0); // Turn tap 3-2 OFF
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// no device updates
			checkDeviceUpdatesSize(hs1, 0);
			checkDeviceUpdatesSize(hs2, 0);
			checkDeviceUpdatesSize(hs3, 0);

			// ON --> ON
			d1_2.status.power = toPowerUnits(0); // Turn tap 1-2 OFF
			hs1.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// no device updates
			checkDeviceUpdatesSize(hs1, 0);
			checkDeviceUpdatesSize(hs2, 0);
			checkDeviceUpdatesSize(hs3, 0);
			//
			hs1.executeRemoteDeviceUpdates(client, log); // clear

		} catch (UnsupportedModelException e) {
			fail(e.toString());
			e.printStackTrace();
		}
	}
}
