package elm.apps;

import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.HomeServerService;
import elm.hs.api.client.AbstractCommandLineClient;
import elm.scheduler.HomeServerLocator;
import elm.scheduler.Scheduler;
import elm.util.ElmLogFormatter;

public class SchedulerApplication extends AbstractCommandLineClient {


	public static void main(String[] args) {
		Logger LOG = null;
		try {
			ElmLogFormatter.init();
			LOG = Logger.getLogger(SchedulerApplication.class.getName());

			Scheduler scheduler = new Scheduler(40_000, 30_000);
			scheduler.setIsAliveCheckDisabled(true); // enable debugger
			// scheduler.setSchedulingIntervalMillis(5_000);
			scheduler.start();

			HomeServerLocator locator = new HomeServerLocator(scheduler, HomeServerService.DEFAULT_PASSWORD);
			locator.start();
			
		} catch (Exception e) {
			if (LOG != null) {
				LOG.log(Level.SEVERE, "Scheduler start failed", e);
			} else {
				e.printStackTrace();
			}
			System.exit(1);
		}
	}

}
