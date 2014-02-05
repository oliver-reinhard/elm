package elm.scheduler;

import static elm.scheduler.model.ModelTestUtil.createHomeServer;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import elm.scheduler.model.HomeServer;

@RunWith(MockitoJUnitRunner.class)
public class SchedulerTest {
	
	static final String PW = "password";

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
		hs1 = createHomeServer(1, 2);
		hsL1 = mock(HomeServerChangeListener.class);
		hs1.addChangeListener(hsL1);

		hs2 = createHomeServer(2, 2);
		hsL2 = mock(HomeServerChangeListener.class);
		hs2.addChangeListener(hsL2);

		hs3 = createHomeServer(3, 2);
		hsL3 = mock(HomeServerChangeListener.class);
		hs3.addChangeListener(hsL3);
		
		scheduler = new Scheduler(2);
		statusL = mock(SchedulerChangeListener.class);
		scheduler.addChangeListener(statusL);
	}
	
	@Test
	public void test() {
		
	}
}
