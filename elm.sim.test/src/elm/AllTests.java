package elm;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import elm.scheduler.AbstractSchedulerTest;
import elm.scheduler.SchedulerTest;
import elm.scheduler.model.DeviceInfoTest;
import elm.scheduler.model.HomeServerTest;
import elm.sim.model.TapPointTest;

@RunWith(Suite.class)
@SuiteClasses({DeviceInfoTest.class, HomeServerTest.class, AbstractSchedulerTest.class, SchedulerTest.class, TapPointTest.class})
public class AllTests {

}
