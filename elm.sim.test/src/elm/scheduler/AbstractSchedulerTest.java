package elm.scheduler;

import static elm.scheduler.model.ModelTestUtil.createHomeServer;
import static elm.scheduler.model.ModelTestUtil.sleep;
import static elm.ui.api.ElmStatus.ERROR;
import static elm.ui.api.ElmStatus.OFF;
import static elm.ui.api.ElmStatus.ON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import elm.scheduler.model.HomeServer;

@RunWith(MockitoJUnitRunner.class)
public class AbstractSchedulerTest {

	static final int NUM_HOME_SERVERS = 2;
	static final int NUM_DEVICES = 0;  // no devices

	HomeServer hs1;
	HomeServer hs2;
	AbstractScheduler scheduler;
	SchedulerChangeListener statusL;

	@Before
	public void setup() {
		hs1 = createHomeServer(1, NUM_DEVICES);
		hs2 = createHomeServer(2, NUM_DEVICES);
		
		scheduler = new AbstractScheduler() {
			@Override
			protected void processDevices() {
				for(HomeServer server : getHomeServers()) {
					if( ! server.isAlive()) {
						setStatus(ERROR);
						return;
					};
				}
			}
		};
		scheduler.setLogLevel(Level.SEVERE);
		statusL = mock(SchedulerChangeListener.class);
		scheduler.addChangeListener(statusL);
	}

	@Test
	public void addRemoveHomeServers() {
		assertEquals(0, scheduler.getHomeServers().size());

		scheduler.addHomeServer(hs1);
		scheduler.addHomeServer(hs2);
		assertEquals(2, scheduler.getHomeServers().size());

		scheduler.removeHomeServer(hs1);
		assertEquals(1, scheduler.getHomeServers().size());
		assertEquals(hs2, scheduler.getHomeServers().get(0));
	}

	@Test
	public void isAliveError() {
		scheduler.addHomeServer(hs1); 
		assertEquals(OFF, scheduler.getStatus());
		assertFalse(hs1.isAlive()); // hs1 => is not alive!
		
		scheduler.runOnce();
		assertEquals(ERROR, scheduler.getStatus());
	}

	@Test
	public void isAliveOk() {
		scheduler.addHomeServer(hs1); 
		assertEquals(OFF, scheduler.getStatus());
		hs1.updateLastHomeServerPollTime();
		assertTrue(hs1.isAlive()); 
		
		scheduler.runOnce();
		assertEquals(ON, scheduler.getStatus());
		sleep(1);
		hs1.updateLastHomeServerPollTime();
		scheduler.runOnce();
		assertEquals(ON, scheduler.getStatus());
	}

	@Test
	public void statusNotifications() {
		assertEquals(OFF, scheduler.getStatus());
		
		scheduler.setStatus(ON);
		verify(statusL).statusChanged(OFF, ON);
	}

	@Test
	public void eventProcessorThreadOnOff() {
		assertEquals(OFF, scheduler.getStatus());
		scheduler.start();
		sleep(10);
		// no device updates => scheduler must not process devices:
		assertEquals(0, scheduler.getSchdedulingRunCount());
		assertEquals(ON, scheduler.getStatus());

		scheduler.stop();
		sleep(10);
		assertEquals(OFF, scheduler.getStatus());
	}

	@Test
	public void eventProcessorThreadDevicesChanged() {
		scheduler.setSchedulingIntervalMillis(20);  // 20 milliseconds
		scheduler.start();
		sleep(10);
		int runCount = scheduler.getSchdedulingRunCount();
		scheduler.devicesManagersUpdated(hs1, false);  // false => do not wake up the scheduler
		assertEquals(runCount, scheduler.getSchdedulingRunCount());
		
		sleep(20); // scheduler should wake up during this time and process devices:
		assertEquals(runCount+1, scheduler.getSchdedulingRunCount());
		
		scheduler.devicesManagersUpdated(hs1, true);  // true => WAKE UP the scheduler
		sleep(2); // give it time to do its work and go back to sleep
		runCount = scheduler.getSchdedulingRunCount();
		scheduler.devicesManagersUpdated(hs1, true);  // true => WAKE UP the scheduler
		sleep(2); // scheduler forced to process devices now
		assertEquals(runCount+1, scheduler.getSchdedulingRunCount());
	}
	
}
