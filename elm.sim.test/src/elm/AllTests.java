package elm;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import elm.hs.api.sim.server.DeviceTapPointAdapterTest;
import elm.scheduler.AbstractSchedulerTest;
import elm.scheduler.SchedulerTest;
import elm.scheduler.model.impl.DeviceControllerTest;
import elm.scheduler.model.impl.HomeServerTest;
import elm.sim.model.TapPointTest;

@RunWith(Suite.class)
@SuiteClasses({DeviceControllerTest.class, HomeServerTest.class, AbstractSchedulerTest.class, SchedulerTest.class, TapPointTest.class, DeviceTapPointAdapterTest.class})
public class AllTests {

}
