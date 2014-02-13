package elm;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import elm.scheduler.AbstractSchedulerTest;
import elm.scheduler.SchedulerTest;
import elm.scheduler.model.DeviceManagerTest;
import elm.scheduler.model.HomeServerTest;
import elm.sim.model.TapPointTest;

@RunWith(Suite.class)
@SuiteClasses({DeviceManagerTest.class, HomeServerTest.class, AbstractSchedulerTest.class, SchedulerTest.class, TapPointTest.class})
public class AllTests {

}
