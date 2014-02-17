package elm.scheduler;

import static elm.scheduler.model.DeviceManager.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceManager.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceManager.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceManager.DeviceStatus.READY;
import static elm.scheduler.model.impl.ModelTestUtil.checkDeviceUpdate;
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
import static org.junit.Assert.assertNull;
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
import elm.scheduler.model.DeviceManager;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.HomeServerChangeListener;
import elm.scheduler.model.RemoteDeviceUpdateClient;
import elm.scheduler.model.UnsupportedModelException;
import elm.scheduler.model.impl.HomeServerImpl;

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

	@Test
	public void schedulingBeginEndOverload() {
		try {
			scheduler.setIsAliveCheckDisabled(true); // enable debugger
			scheduler.addHomeServer(hs1);
			scheduler.addHomeServer(hs2);
			scheduler.addHomeServer(hs3);

			hs1.updateLastHomeServerPollTime();  // otherwise HomeServer is not alive 
			hs2.updateLastHomeServerPollTime();
			hs3.updateLastHomeServerPollTime();

			assertEquals(OFF, scheduler.getStatus());

			int runCount = scheduler.getSchdedulingRunCount();
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(runCount + 1, scheduler.getSchdedulingRunCount());

			// ON --> ON
			List<Device> hs1_Devices = createDevicesWithStatus(1, NUM_DEVICES, 0);
			Device d1_1 = hs1_Devices.get(0);
			Device d1_2 = hs1_Devices.get(1);
			d1_2.status.power = toPowerUnits(20_000); // Turn tap 1-2 ON
			assertEquals(READY, hs1.getDeviceManager(d1_2.id).getStatus());
			runCount = scheduler.getSchdedulingRunCount();
			//
			// the following command would normally trigger a run but we have not started the scheduler => no run
			hs1.updateDeviceManagers(hs1_Devices);
			assertEquals(runCount, scheduler.getSchdedulingRunCount());
			assertEquals(CONSUMPTION_STARTED, hs1.getDeviceManager(d1_2.id).getStatus());
			//
			// start a scheduler run manually:
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(runCount + 1, scheduler.getSchdedulingRunCount());
			assertEquals(READY, hs1.getDeviceManager(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceManager(d1_2.id).getStatus());
			//
			// home server 1: check device updates
			assertEquals(1, ((HomeServerImpl)hs1).getPendingUpdates().size());
			checkDeviceUpdate(hs1, d1_2, DeviceManager.UNLIMITED_POWER);
			//
			// home server 1: process device updates => "execute" (and clear) the device updates
			RemoteDeviceUpdateClient client = mock(RemoteDeviceUpdateClient.class);
			hs1.executeRemoteDeviceUpdates(client, log);

			// ON --> SATURATION
			List<Device> hs2_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0);
			Device d2_1 = hs2_Devices.get(0);
			Device d2_2 = hs2_Devices.get(1);
			d2_2.status.power = toPowerUnits(20_000); // Turn tap 2-2 ON
			hs2.updateDeviceManagers(hs2_Devices);
			assertEquals(CONSUMPTION_STARTED, hs2.getDeviceManager(d2_2.id).getStatus());
			//
			// scheduler: act
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(READY, hs2.getDeviceManager(d2_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs2.getDeviceManager(d2_2.id).getStatus());
			//
			// home server 2: check device updates
			assertEquals(1, ((HomeServerImpl)hs2).getPendingUpdates().size());
			checkDeviceUpdate(hs2, d2_2, DeviceManager.UNLIMITED_POWER);
			//
			// home server 2: execute & clear device updates
			hs2.executeRemoteDeviceUpdates(client, log);

			// SATURATION --> OVERLOAD
			List<Device> hs3_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0);
			Device d3_1 = hs3_Devices.get(0);
			Device d3_2 = hs3_Devices.get(1);
			d3_2.status.power = toPowerUnits(20_000); // Turn tap 3-2 ON
			hs3.updateDeviceManagers(hs3_Devices);
			assertEquals(CONSUMPTION_STARTED, hs3.getDeviceManager(d3_2.id).getStatus()); // tap 3-2 is CONSUMING (new)
			//
			// scheduler: act
			scheduler.runOnce();
			assertEquals(OVERLOAD, scheduler.getStatus());
			assertTrue(scheduler.isInOverloadMode());
			assertEquals(READY, hs1.getDeviceManager(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceManager(d1_2.id).getStatus());
			assertEquals(READY, hs2.getDeviceManager(d2_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs2.getDeviceManager(d2_2.id).getStatus());
			assertEquals(READY, hs3.getDeviceManager(d3_1.id).getStatus());
			assertEquals(CONSUMPTION_DENIED, hs3.getDeviceManager(d3_2.id).getStatus()); // tap 3-2 is WAITING
			//
			// check device updates at home server 1:
			assertEquals(1, ((HomeServerImpl)hs1).getPendingUpdates().size());
			checkDeviceUpdate(hs1, d1_1, DeviceManager.NO_POWER);
			//
			// home server 1: execute & clear device updates
			hs1.executeRemoteDeviceUpdates(client, log);
			//
			// check device updates at home server 2:
			assertEquals(1, ((HomeServerImpl)hs2).getPendingUpdates().size());
			checkDeviceUpdate(hs2, d2_1, DeviceManager.NO_POWER);
			//
			// home server 2: execute & clear device updates
			hs2.executeRemoteDeviceUpdates(client, log);
			//
			// check device updates at home server 3:
			assertEquals(2, ((HomeServerImpl)hs3).getPendingUpdates().size());
			checkDeviceUpdate(hs3, d3_1, DeviceManager.NO_POWER);
			checkDeviceUpdate(hs3, d3_2, DeviceManager.NO_POWER); // No power!
			//
			// home server 3: execute & clear device updates
			hs3.executeRemoteDeviceUpdates(client, log);
			
			// OVERLOAD --> SATURATION
			d1_2.status.power = toPowerUnits(10_000); // Reduce tap 1-2 power => tap 3-2 can run as well
			hs1.updateDeviceManagers(hs1_Devices);
			//
			// scheduler: act
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(READY, hs1.getDeviceManager(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceManager(d1_2.id).getStatus());
			assertEquals(READY, hs2.getDeviceManager(d2_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs2.getDeviceManager(d2_2.id).getStatus());
			assertEquals(READY, hs3.getDeviceManager(d3_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs3.getDeviceManager(d3_2.id).getStatus()); // tap 3-2 CONSUMING now
			
			//
			// check device updates at home server 1:
			checkDeviceUpdate(hs1, d1_1, DeviceManager.UNLIMITED_POWER);
			//
			// home server 1: execute & clear device updates
			hs1.executeRemoteDeviceUpdates(client, log);
			assertNull(((HomeServerImpl)hs1).getPendingUpdates());
			//
			// check device updates at home server 2:
			checkDeviceUpdate(hs2, d2_1, DeviceManager.UNLIMITED_POWER);
			//
			// home server 2: execute & clear device updates
			hs2.executeRemoteDeviceUpdates(client, log);
			assertNull(((HomeServerImpl)hs2).getPendingUpdates());
			//
			// check device updates at home server 3:
			checkDeviceUpdate(hs3, d3_1, DeviceManager.UNLIMITED_POWER);
			checkDeviceUpdate(hs3, d3_2, DeviceManager.UNLIMITED_POWER); // Consuming now!
			//
			// home server 3: execute & clear device updates
			hs3.executeRemoteDeviceUpdates(client, log);
			assertNull(((HomeServerImpl)hs3).getPendingUpdates());
			
			// SATURATION --> ON
			d2_2.status.power = toPowerUnits(0); // Turn tap 1-2 OFF
			hs2.updateDeviceManagers(hs2_Devices);
			//
			// scheduler: act
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(READY, hs1.getDeviceManager(d1_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs1.getDeviceManager(d1_2.id).getStatus()); // tap 1-2 still consuming
			assertEquals(READY, hs2.getDeviceManager(d2_1.id).getStatus());
			assertEquals(READY, hs2.getDeviceManager(d2_2.id).getStatus()); // tap 2-2 is OFF
			assertEquals(READY, hs3.getDeviceManager(d3_1.id).getStatus());
			assertEquals(CONSUMPTION_APPROVED, hs3.getDeviceManager(d3_2.id).getStatus()); // tap 3-2 still consuming
			//
			// check device updates at home servers:
			assertNull(((HomeServerImpl)hs1).getPendingUpdates());
			assertNull(((HomeServerImpl)hs2).getPendingUpdates());
			assertNull(((HomeServerImpl)hs3).getPendingUpdates());

		} catch (UnsupportedModelException e) {
			fail(e.toString());
			e.printStackTrace();
		}

	}
}
