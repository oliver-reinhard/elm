package elm.apps;

import java.io.IOException;
import java.net.URI;

import elm.hs.api.client.AbstractCommandLineClient;
import elm.scheduler.HomeServerController;
import elm.scheduler.Scheduler;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.impl.HomeServerImpl;
import elm.ui.api.ElmUserFeedbackClient;
import elm.util.ElmLogFormatter;

public class SchedulerApplication extends AbstractCommandLineClient {
	
	public static void main(String[] args) throws Exception {
		try {
			ElmLogFormatter.init();
		} catch (SecurityException | IOException e) {
			System.exit(1);
		}
		
//		final URI uri = URI.create("http://localhost:9090");
//		final URI uri = URI.create("http://192.168.178.25:9090");
		final URI uri = URI.create("http://169.254.177.184:9090");
		HomeServer hs = new HomeServerImpl(uri, "geheim");
		
		ElmUserFeedbackClient feedbackClient = new ElmUserFeedbackClient(uri, "geheim");
		feedbackClient.start();
		
		HomeServerController hsManager = new HomeServerController(hs, feedbackClient);
//		hsManager.setPollingIntervalMillis(5_000);
		
		Scheduler scheduler = new Scheduler(40_000, 30_000);
		scheduler.setIsAliveCheckDisabled(true); // enable debugger
//		scheduler.setSchedulingIntervalMillis(5_000);
		scheduler.addHomeServer(hs);
		
		hsManager.start();
		scheduler.start();
	}

}
