package elm.apps;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.HomeServerService;
import elm.hs.api.client.AbstractCommandLineClient;
import elm.scheduler.HomeServerLocator;
import elm.scheduler.Scheduler;
import elm.ui.api.ElmUserFeedbackClient;
import elm.util.ElmLogFormatter;

public class SchedulerApplication extends AbstractCommandLineClient {

	private static final Logger LOG = Logger.getLogger(SchedulerApplication.class.getName());

	public static void main(String[] args) {
		try {
			ElmLogFormatter.init();

			 final URI uri = URI.create("http://localhost:9090");
			// final URI uri = URI.create("http://192.168.178.25:9090");
//			final URI uri = URI.create("http://169.254.177.184:9090");

			ElmUserFeedbackClient feedbackClient = new ElmUserFeedbackClient(uri, HomeServerService.DEFAULT_PASSWORD);
			feedbackClient.start();

			Scheduler scheduler = new Scheduler(40_000, 30_000);
			scheduler.setIsAliveCheckDisabled(true); // enable debugger
			// scheduler.setSchedulingIntervalMillis(5_000);
			scheduler.start();

			HomeServerLocator locator = new HomeServerLocator(scheduler, feedbackClient, HomeServerService.DEFAULT_PASSWORD);
			locator.start();
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Scheduler start failed", e);
			System.exit(1);
		}
	}

}
