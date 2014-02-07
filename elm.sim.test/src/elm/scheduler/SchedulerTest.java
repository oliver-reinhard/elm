package elm.scheduler;

import static elm.scheduler.model.ModelTestUtil.createDevices;
import static elm.scheduler.model.ModelTestUtil.createHomeServer;
import static elm.scheduler.model.ModelTestUtil.getDeviceUpdate;
import static elm.scheduler.model.ModelTestUtil.sleep;
import static elm.scheduler.model.ModelTestUtil.toPowerUnits;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.model.Device;
import static elm.scheduler.ElmStatus.*;
import elm.scheduler.model.AbstractDeviceUpdate;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.SetPowerLimit;
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

		scheduler = new Scheduler(50_000);
		scheduler.setLogLevel(Level.SEVERE);
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
			List<Device> hs1_Devices = createDevices(1, NUM_DEVICES, 0);
			Device d1_1 = hs1_Devices.get(0);
			Device d1_2 = hs1_Devices.get(1);
			d1_2.status.power = toPowerUnits(20_000); // Turn tap 1-2 ON
			assertEquals(DeviceInfo.State.READY, hs1.getDeviceInfo(d1_2.id).getState());
			runCount = scheduler.getSchdedulingRunCount();
			// this would normally trigger a run but we have not started the scheduler => no run
			hs1.updateDeviceInfos(hs1_Devices);
			assertEquals(runCount, scheduler.getSchdedulingRunCount());
			assertEquals(DeviceInfo.State.CONSUMING, hs1.getDeviceInfo(d1_2.id).getState());
			// start a run manually:
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(runCount + 1, scheduler.getSchdedulingRunCount());
			assertEquals(DeviceInfo.State.CONSUMING, hs1.getDeviceInfo(d1_2.id).getState());

			// ON --> SATURATION
			List<Device> hs2_Devices = createDevices(2, NUM_DEVICES, 0);
			Device d2_1 = hs2_Devices.get(0);
			Device d2_2 = hs2_Devices.get(1);
			d2_2.status.power = toPowerUnits(20_000); // Turn tap 2-2 ON
			hs2.updateDeviceInfos(hs2_Devices);
			assertEquals(DeviceInfo.State.CONSUMING, hs2.getDeviceInfo(d2_2.id).getState());
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			assertEquals(DeviceInfo.State.CONSUMING, hs2.getDeviceInfo(d2_2.id).getState());

			// SATURATION --> OVERLOAD
			List<Device> hs3_Devices = createDevices(2, NUM_DEVICES, 0);
			Device d3_1 = hs3_Devices.get(0);
			Device d3_2 = hs3_Devices.get(1);
			d3_2.status.power = toPowerUnits(20_000); // Turn tap 3-2 ON
			hs3.updateDeviceInfos(hs3_Devices);
			assertEquals(DeviceInfo.State.CONSUMING, hs3.getDeviceInfo(d3_2.id).getState()); // tap 3-2 is CONSUMING (new)
			scheduler.runOnce();
			assertEquals(OVERLOAD, scheduler.getStatus());
			assertTrue(scheduler.isInOverloadMode());
			//
			List<AbstractDeviceUpdate> hs1_Updates = ((HomeServerImpl)hs1).getPendingUpdates();
			assertEquals(2, hs1_Updates.size());
			assertPowerLimit(hs1, d1_1, DeviceInfo.NO_POWER);
			assertEquals(DeviceInfo.State.READY, hs1.getDeviceInfo(d1_1.id).getState());
			assertPowerLimit(hs1, d1_2, DeviceInfo.UNLIMITED_POWER);
			assertEquals(DeviceInfo.State.CONSUMING, hs1.getDeviceInfo(d1_2.id).getState());
			verify(hsL1).deviceUpdatesPending(hs1, true);
			// "execute" (and clear) the device updates
			HomeServerInternalApiClient client = mock(HomeServerInternalApiClient.class);
			hs1.executeDeviceUpdates(client, log);
			assertEquals(DeviceInfo.State.WAITING, hs1.getDeviceInfo(d1_1.id).getState());
			assertEquals(DeviceInfo.State.CONSUMING, hs1.getDeviceInfo(d1_2.id).getState());
			//
			List<AbstractDeviceUpdate> hs2_Updates = ((HomeServerImpl)hs2).getPendingUpdates();
			assertEquals(2, hs2_Updates.size());
			assertPowerLimit(hs2, d2_1, DeviceInfo.NO_POWER);
			assertEquals(DeviceInfo.State.READY, hs2.getDeviceInfo(d2_1.id).getState());
			assertPowerLimit(hs2, d2_2, DeviceInfo.UNLIMITED_POWER);
			assertEquals(DeviceInfo.State.CONSUMING, hs2.getDeviceInfo(d2_2.id).getState());
			verify(hsL2).deviceUpdatesPending(hs2, true);
			hs2.executeDeviceUpdates(client, log);
			assertEquals(DeviceInfo.State.WAITING, hs2.getDeviceInfo(d2_1.id).getState());
			assertEquals(DeviceInfo.State.CONSUMING, hs2.getDeviceInfo(d2_2.id).getState());
			//
			List<AbstractDeviceUpdate> hs3_Updates = ((HomeServerImpl)hs3).getPendingUpdates();
			assertEquals(2, hs3_Updates.size());
			assertPowerLimit(hs3, d3_1, DeviceInfo.NO_POWER);
			assertEquals(DeviceInfo.State.READY, hs3.getDeviceInfo(d3_1.id).getState());
			assertPowerLimit(hs3, d3_2, DeviceInfo.NO_POWER); // No power!
			assertEquals(DeviceInfo.State.CONSUMING, hs3.getDeviceInfo(d3_2.id).getState()); // tap 3-2 still CONSUMING
			verify(hsL3).deviceUpdatesPending(hs3, true);
			hs3.executeDeviceUpdates(client, log);
			assertEquals(DeviceInfo.State.WAITING, hs3.getDeviceInfo(d3_1.id).getState());
			assertEquals(DeviceInfo.State.WAITING, hs3.getDeviceInfo(d3_2.id).getState()); // tap 3-2 is WAITING
			
			// OVERLOAD --> SATURATION
			d1_2.status.power = toPowerUnits(10_000); // Reduce tap 1-2 power => tap 3-2 can run as well
			hs1.updateDeviceInfos(hs1_Devices);
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertPowerLimit(hs1, d1_1, DeviceInfo.UNLIMITED_POWER);
			assertPowerLimit(hs1, d1_2, DeviceInfo.UNLIMITED_POWER);
			hs1.executeDeviceUpdates(client, log);
			assertEquals(DeviceInfo.State.READY, hs1.getDeviceInfo(d1_1.id).getState());
			assertEquals(DeviceInfo.State.CONSUMING, hs1.getDeviceInfo(d1_2.id).getState());
			//
			assertPowerLimit(hs2, d2_1, DeviceInfo.UNLIMITED_POWER);
			assertPowerLimit(hs2, d2_2, DeviceInfo.UNLIMITED_POWER);
			hs2.executeDeviceUpdates(client, log);
			assertEquals(DeviceInfo.State.READY, hs2.getDeviceInfo(d2_1.id).getState());
			assertEquals(DeviceInfo.State.CONSUMING, hs2.getDeviceInfo(d2_2.id).getState());

			assertPowerLimit(hs3, d3_1, DeviceInfo.UNLIMITED_POWER);
			assertPowerLimit(hs3, d3_2, DeviceInfo.UNLIMITED_POWER); // Consuming now!
			hs3.executeDeviceUpdates(client, log);
			assertEquals(DeviceInfo.State.READY, hs3.getDeviceInfo(d3_1.id).getState());
			assertEquals(DeviceInfo.State.CONSUMING, hs3.getDeviceInfo(d3_2.id).getState()); // tap 3-2 CONSUMING now
			

		} catch (UnsupportedModelException e) {
			assertTrue(false);
		}

	}

	private void assertPowerLimit(HomeServer server, Device device, int expectedLimitWatt) {
		SetPowerLimit limit = (SetPowerLimit) getDeviceUpdate(server, device);
		assertNotNull(limit);
		assertEquals(expectedLimitWatt, limit.getApprovedPowerLimitWatt());
	}
}
