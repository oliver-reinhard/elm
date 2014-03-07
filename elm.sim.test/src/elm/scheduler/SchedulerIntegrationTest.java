package elm.scheduler;

import static elm.hs.api.model.ElmStatus.OFF;
import static elm.hs.api.model.ElmStatus.ON;
import static elm.hs.api.model.ElmStatus.OVERLOAD;
import static elm.hs.api.model.ElmStatus.SATURATION;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceController.DeviceStatus.READY;
import static elm.scheduler.model.impl.ModelTestUtil.FLOW_OFF;
import static elm.scheduler.model.impl.ModelTestUtil.FLOW_ON;
import static elm.scheduler.model.impl.ModelTestUtil.checkDeviceUpdate;
import static elm.scheduler.model.impl.ModelTestUtil.checkDeviceUpdatesSize;
import static elm.scheduler.model.impl.ModelTestUtil.checkNoDeviceUpdates;
import static elm.scheduler.model.impl.ModelTestUtil.checkNoUserFeedback;
import static elm.scheduler.model.impl.ModelTestUtil.checkUserFeedback;
import static elm.scheduler.model.impl.ModelTestUtil.createDeviceWithStatus;
import static elm.scheduler.model.impl.ModelTestUtil.createDevicesWithInfo;
import static elm.scheduler.model.impl.ModelTestUtil.createDevicesWithStatus;
import static elm.scheduler.model.impl.ModelTestUtil.createHomeServer;
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
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.util.ClientException;

@RunWith(MockitoJUnitRunner.class)
public class SchedulerIntegrationTest {

	static final int NUM_HOME_SERVERS = 3;
	static final int NUM_DEVICES = 2;

	final Logger log = Logger.getLogger(getClass().getName());

	ElmUserFeedbackManager feedbackManager;
	ElmUserFeedbackClient feedbackClient;
	ElmTestTimeService timeService;
	HomeServer hs1;
	HomeServer hs2;
	HomeServer hs3;
	HomeServerChangeListener hsL1;
	HomeServerChangeListener hsL2;
	HomeServerChangeListener hsL3;
	ElmScheduler scheduler;
	SchedulerChangeListener statusL;

	@Before
	public void setup() {
		feedbackManager = new ElmUserFeedbackManager();
		feedbackClient = mock(ElmUserFeedbackClient.class);
		timeService = new ElmTestTimeService();

		hs1 = createHomeServer(1, NUM_DEVICES, feedbackManager, feedbackClient, timeService); // also initializes the devices with device.status
		hsL1 = mock(HomeServerChangeListener.class);
		hs1.addChangeListener(hsL1);

		hs2 = createHomeServer(2, NUM_DEVICES, feedbackManager, feedbackClient, timeService);
		hsL2 = mock(HomeServerChangeListener.class);
		hs2.addChangeListener(hsL2);

		hs3 = createHomeServer(3, NUM_DEVICES, feedbackManager, feedbackClient, timeService);
		hsL3 = mock(HomeServerChangeListener.class);
		hs3.addChangeListener(hsL3);

		scheduler = new ElmScheduler(50_000, 30_000);
		scheduler.setTimeService(timeService);
		statusL = mock(SchedulerChangeListener.class);
		scheduler.addChangeListener(statusL);
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
			List<Device> hs1_Devices = createDevicesWithStatus(1, NUM_DEVICES, 0, FLOW_OFF);
			Device d1_1 = hs1_Devices.get(0);
			hs1_Devices.set(1, createDeviceWithStatus(1, 2, 20_000, FLOW_ON)); // Turn tap 1-2 ON
			Device d1_2 = hs1_Devices.get(1);
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
			List<Device> hs2_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0, FLOW_OFF);
			Device d2_1 = hs2_Devices.get(0);
			Device d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			//
			List<Device> hs3_Devices = createDevicesWithStatus(3, NUM_DEVICES, 0, FLOW_OFF);
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
			hs2_Devices.set(1, createDeviceWithStatus(2, 2, 20_000, FLOW_ON)); // Turn tap 2-2 ON)
			d2_2 = hs2_Devices.get(1);
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
			hs3_Devices.set(1, createDeviceWithStatus(3, 2, 20_000, FLOW_ON)); // Turn tap 3-2 ON
			d3_2 = hs3_Devices.get(1);
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
			hs1_Devices.set(1, createDeviceWithStatus(1, 2, 10_000, FLOW_ON)); // Reduce tap 1-2 power => tap 3-2 can run as well
			d1_2 = hs1_Devices.get(1);
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
			hs2_Devices.set(1, createDeviceWithStatus(2, 2, 0, FLOW_OFF)); // Turn tap 2-2 OFF
			d2_2 = hs2_Devices.get(1);
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
			hs1_Devices.set(1, createDeviceWithStatus(1, 2, 0, FLOW_OFF)); // Turn tap 1-2 OFF
			d1_2 = hs1_Devices.get(1);
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
			hs3_Devices.set(1, createDeviceWithStatus(3, 2, 0, FLOW_OFF)); // Turn tap 3-2 OFF
			d3_2 = hs3_Devices.get(1);
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

			// ON --> OFF
			scheduler.stop();
			scheduler.runOnce();
			assertEquals(OFF, scheduler.getStatus());
			assertFalse(scheduler.isInOverloadMode());
			//
			assertEquals(READY, hs1.getDeviceController(d1_1.id).getStatus());
			assertEquals(READY, hs1.getDeviceController(d1_2.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_1.id).getStatus());
			assertEquals(READY, hs2.getDeviceController(d2_2.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_1.id).getStatus());
			assertEquals(READY, hs3.getDeviceController(d3_2.id).getStatus());

		} catch (UnsupportedDeviceModelException e) {
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

			List<Device> hs1_Devices = createDevicesWithInfo(1, NUM_DEVICES);
			Device d1_1 = hs1_Devices.get(0);
			Device d1_2 = hs1_Devices.get(1);

			List<Device> hs2_Devices = createDevicesWithInfo(2, NUM_DEVICES);
			Device d2_1 = hs2_Devices.get(0);
			Device d2_2 = hs2_Devices.get(1);

			List<Device> hs3_Devices = createDevicesWithInfo(3, NUM_DEVICES);
			Device d3_1 = hs3_Devices.get(0);
			Device d3_2 = hs3_Devices.get(1);

			checkDeviceUpdatesSize(hs1, 2);
			checkDeviceUpdatesSize(hs2, 2);
			checkDeviceUpdatesSize(hs3, 2);
			checkDeviceUpdate(hs1, d1_1, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs1, d1_2, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs2, d2_1, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs2, d2_2, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs3, d3_1, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs3, d3_2, DeviceController.UNLIMITED_POWER);

			checkUserFeedback(hs1, d1_1, ON, 0);
			checkUserFeedback(hs1, d1_2, ON, 0);
			checkUserFeedback(hs2, d2_1, ON, 0);
			checkUserFeedback(hs2, d2_2, ON, 0);
			checkUserFeedback(hs3, d3_1, ON, 0);
			checkUserFeedback(hs3, d3_2, ON, 0);

			// clear stored user feedback
			feedbackManager.sendFeedack(feedbackClient);

			// home server 1..3: process device updates => "execute" (and clear) the device updates
			RemoteDeviceUpdateClient client = mock(RemoteDeviceUpdateClient.class);
			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			scheduler.addHomeServer(hs1);
			checkNoDeviceUpdates(hs1);
			checkNoUserFeedback(hs1);

			scheduler.addHomeServer(hs2);
			checkNoDeviceUpdates(hs1); // no influence on hs1
			checkNoDeviceUpdates(hs2);

			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);

			scheduler.addHomeServer(hs3);

			hs1.updateLastHomeServerPollTime(); // otherwise HomeServer is not alive
			hs2.updateLastHomeServerPollTime();
			hs3.updateLastHomeServerPollTime();
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());

			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);

			checkUserFeedback(hs1, d1_1, ON, 0);
			checkUserFeedback(hs1, d1_2, ON, 0);
			checkUserFeedback(hs2, d2_1, ON, 0);
			checkUserFeedback(hs2, d2_2, ON, 0);
			checkUserFeedback(hs3, d3_1, ON, 0);
			checkUserFeedback(hs3, d3_2, ON, 0);

			feedbackManager.sendFeedack(feedbackClient); // clear

			// ON --> ON
			hs1_Devices = createDevicesWithStatus(1, NUM_DEVICES, 0, FLOW_OFF);
			d1_1 = hs1_Devices.get(0);
			d1_2 = hs1_Devices.get(1);
			hs1.updateDeviceControllers(hs1_Devices);
			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			hs2_Devices = createDevicesWithStatus(2, NUM_DEVICES, 0, FLOW_OFF);
			d2_1 = hs2_Devices.get(0);
			d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			hs3_Devices = createDevicesWithStatus(3, NUM_DEVICES, 0, FLOW_OFF);
			d3_1 = hs3_Devices.get(0);
			d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			// no device updates anywhere
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);
			// User feedback should not have changed from "ON, 0 ms":
			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			// ON --> ON
			hs1_Devices.set(1, createDeviceWithStatus(1, 2, 20_000, FLOW_ON)); // *** Turn tap 1-2 ON
			d1_2 = hs1_Devices.get(1);
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1); // has not changed from previous update: UNLIMITED_POWER
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);
			// User feedback should not have changed from "ON, 0 ms":
			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			// ON --> ON
			hs2_Devices.set(1, createDeviceWithStatus(2, 2, 5_000, FLOW_ON)); // *** Turn tap 2-2 ON (low)
			d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2); // has not changed from previous update: UNLIMITED_POWER
			checkNoDeviceUpdates(hs3);
			// User feedback should not have changed from "ON, 0 ms":
			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			// ON --> SATURATION
			hs2_Devices.set(1, createDeviceWithStatus(2, 2, 20_000, FLOW_ON)); // *** Turn tap 2-2 ON (high)
			d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1); // has not changed from previous update: UNLIMITED_POWER
			checkNoDeviceUpdates(hs2); // has not changed from previous update: UNLIMITED_POWER
			checkNoDeviceUpdates(hs3); // has not changed from previous update: UNLIMITED_POWER

			checkUserFeedback(hs1, d1_1, SATURATION, 0);
			checkNoUserFeedback(hs1, d1_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs2, d2_1, SATURATION, 0);
			checkNoUserFeedback(hs2, d2_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs3, d3_1, SATURATION, 0);
			checkUserFeedback(hs3, d3_2, SATURATION, 0);

			feedbackManager.sendFeedack(feedbackClient); // clear

			// SATURATION --> SATURATION
			hs1_Devices.set(0, createDeviceWithStatus(1, 1, 5_000, FLOW_ON)); // *** Turn tap 1-1 ON (low)
			d1_1 = hs1_Devices.get(0);
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);
			// User feedback should not have changed from "ON, 0 ms" and "SATURATION, 0 ms", respectively:
			checkUserFeedback(hs1, d1_1, ON, 0);
			checkNoUserFeedback(hs1, d1_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			feedbackManager.sendFeedack(feedbackClient); // clear

			// SATURATION --> SATURATION
			hs1_Devices.set(0, createDeviceWithStatus(1, 1, 0, FLOW_OFF)); // *** Turn tap 1-1 OFF
			d1_1 = hs1_Devices.get(0);
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);

			checkUserFeedback(hs1, d1_1, SATURATION, 0);
			checkNoUserFeedback(hs1, d1_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			feedbackManager.sendFeedack(feedbackClient); // clear

			// SATURATION --> OVERLOAD
			hs3_Devices.set(1, createDeviceWithStatus(3, 2, 15_000, FLOW_ON)); // *** Turn tap 3-2 ON => will not be allowed
			d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(OVERLOAD, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, DeviceController.NO_POWER);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, d2_1, DeviceController.NO_POWER);
			checkDeviceUpdatesSize(hs3, 2);
			checkDeviceUpdate(hs3, d3_1, DeviceController.NO_POWER);
			checkDeviceUpdate(hs3, d3_2, DeviceController.NO_POWER);

			checkUserFeedback(hs1, d1_1, OVERLOAD, 9981);
			checkNoUserFeedback(hs1, d1_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs2, d2_1, OVERLOAD, 9981);
			checkNoUserFeedback(hs2, d2_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs3, d3_1, OVERLOAD, 9981);
			checkUserFeedback(hs3, d3_2, OVERLOAD, 9977); // has a shorter wait time since it is TRYING TO CONSUME

			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			feedbackManager.sendFeedack(feedbackClient); // clear

			// OVERLOAD --> OVERLOAD
			hs3_Devices.set(1, createDeviceWithStatus(3, 2, 11_000, FLOW_ON)); // *** Turn tap 3-2 DOWN
			d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(OVERLOAD, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);

			checkUserFeedback(hs1, d1_1, OVERLOAD, 9977);
			checkNoUserFeedback(hs1, d1_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs2, d2_1, OVERLOAD, 9977);
			checkNoUserFeedback(hs2, d2_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs3, d3_1, OVERLOAD, 9977);
			checkUserFeedback(hs3, d3_2, OVERLOAD, 9973); // has a shorter wait time since it is TRYING TO CONSUME

			feedbackManager.sendFeedack(feedbackClient); // clear

			// OVERLOAD --> SATURATION
			hs3_Devices.set(1, createDeviceWithStatus(3, 2, 0, FLOW_OFF)); // *** Turn tap 3-2 OFF => scald-protection removed
			d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkDeviceUpdatesSize(hs1, 1);
			checkDeviceUpdate(hs1, d1_1, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs2, 1);
			checkDeviceUpdate(hs2, d2_1, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdatesSize(hs3, 2);
			checkDeviceUpdate(hs3, d3_1, DeviceController.UNLIMITED_POWER);
			checkDeviceUpdate(hs3, d3_2, DeviceController.UNLIMITED_POWER);

			checkUserFeedback(hs1, d1_1, SATURATION, 0);
			checkNoUserFeedback(hs1, d1_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs2, d2_1, SATURATION, 0);
			checkNoUserFeedback(hs2, d2_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs3, d3_1, SATURATION, 0);
			checkUserFeedback(hs3, d3_2, SATURATION, 0); 

			hs1.executeRemoteDeviceUpdates(client, log); // clear
			hs2.executeRemoteDeviceUpdates(client, log); // clear
			hs3.executeRemoteDeviceUpdates(client, log); // clear

			feedbackManager.sendFeedack(feedbackClient); // clear
			

			// SATURATION --> SATURATION
			hs3_Devices.set(1, createDeviceWithStatus(3, 2, 6000, FLOW_ON)); // *** Turn tap 3-2 on LOW => will now be allowed
			d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(SATURATION, scheduler.getStatus());
			
			checkNoDeviceUpdates(hs3);
			
			checkUserFeedback(hs3, d3_2, ON, 0); 
			

			// SATURATION --> ON
			hs2_Devices.set(1, createDeviceWithStatus(2, 2, 5_000, FLOW_ON)); // *** Turn tap 2-2 DOWN (low)
			d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// home server 1..3: check device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);

			checkUserFeedback(hs1, d1_1, ON, 0);
			checkNoUserFeedback(hs1, d1_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs2, d2_1, ON, 0);
			checkNoUserFeedback(hs2, d2_2); // tap ON, feedback has not changed from previous update "ON, 0 ms"
			checkUserFeedback(hs3, d3_1, ON, 0);
			checkUserFeedback(hs3, d3_2, ON, 0);
			//

			feedbackManager.sendFeedack(feedbackClient); // clear

			// ON --> ON
			hs2_Devices.set(1, createDeviceWithStatus(2, 2, 0, FLOW_OFF)); // *** Turn tap 2-2 OFF
			d2_2 = hs2_Devices.get(1);
			hs2.updateDeviceControllers(hs2_Devices);
			hs3_Devices.set(1, createDeviceWithStatus(3, 2, 0, FLOW_OFF)); // *** Turn tap 3-2 OFF
			d3_2 = hs3_Devices.get(1);
			hs3.updateDeviceControllers(hs3_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// no device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);

			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			// ON --> ON
			hs1_Devices.set(1, createDeviceWithStatus(1, 2, 0, FLOW_OFF)); // *** Turn tap 1-2 OFF
			d1_2 = hs1_Devices.get(1);
			hs1.updateDeviceControllers(hs1_Devices);
			//
			// scheduler: run
			scheduler.runOnce();
			assertEquals(ON, scheduler.getStatus());
			//
			// no device updates
			checkNoDeviceUpdates(hs1);
			checkNoDeviceUpdates(hs2);
			checkNoDeviceUpdates(hs3);

			checkNoUserFeedback(hs1);
			checkNoUserFeedback(hs2);
			checkNoUserFeedback(hs3);

			// ON --> OFF
			scheduler.stop();
			scheduler.runOnce();

			checkDeviceUpdatesSize(hs1, 2);
			checkDeviceUpdate(hs1, d1_1, DeviceController.NO_POWER);
			checkDeviceUpdate(hs1, d1_2, DeviceController.NO_POWER);
			checkDeviceUpdatesSize(hs2, 2);
			checkDeviceUpdate(hs2, d2_1, DeviceController.NO_POWER);
			checkDeviceUpdate(hs2, d2_2, DeviceController.NO_POWER);
			checkDeviceUpdatesSize(hs3, 2);
			checkDeviceUpdate(hs3, d3_1, DeviceController.NO_POWER);
			checkDeviceUpdate(hs3, d3_2, DeviceController.NO_POWER);

			checkUserFeedback(hs1, d1_1, OFF, 0);
			checkUserFeedback(hs1, d1_2, OFF, 0);
			checkUserFeedback(hs2, d2_1, OFF, 0);
			checkUserFeedback(hs2, d2_2, OFF, 0);
			checkUserFeedback(hs3, d3_1, OFF, 0);
			checkUserFeedback(hs3, d3_2, OFF, 0);

		} catch (UnsupportedDeviceModelException | ClientException e) {
			fail(e.toString());
			e.printStackTrace();
		}
	}
}
