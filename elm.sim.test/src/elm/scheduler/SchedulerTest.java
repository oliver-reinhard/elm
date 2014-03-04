package elm.scheduler;

import static elm.hs.api.model.ElmStatus.ERROR;
import static elm.hs.api.model.ElmStatus.OFF;
import static elm.hs.api.model.ElmStatus.ON;
import static elm.scheduler.model.impl.ModelTestUtil.createHomeServer;
import static elm.scheduler.model.impl.ModelTestUtil.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import elm.scheduler.model.HomeServer;

@RunWith(MockitoJUnitRunner.class)
public class SchedulerTest {

	static final int NUM_DEVICES = 2;

	final Logger log = Logger.getLogger(getClass().getName());

	ElmUserFeedbackManager feedbackManager;
	ElmUserFeedbackClient feedbackClient;
	HomeServer hs1;
	ElmScheduler scheduler;
	SchedulerChangeListener statusL;

	@Before
	public void setup() {
		feedbackManager = new ElmUserFeedbackManager();
		feedbackClient = mock(ElmUserFeedbackClient.class);
		
		hs1 = createHomeServer(1, NUM_DEVICES, feedbackManager, feedbackClient);  // also initializes the devices with device.status

		scheduler = new ElmScheduler(50_000, 30_000);
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
}
