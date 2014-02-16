package elm.apps;

import java.net.URI;
import java.util.logging.Level;

import elm.hs.api.client.AbstractCommandLineClient;
import elm.scheduler.HomeServerManager;
import elm.scheduler.Scheduler;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.impl.HomeServerImpl;
import elm.ui.api.ElmUserFeedbackClient;

public class SchedulerApplication extends AbstractCommandLineClient {
	
	public static void main(String[] args) throws Exception {
		final URI uri = URI.create("http://localhost:9090");
		HomeServer hs = new HomeServerImpl(uri, "geheim");
		
		ElmUserFeedbackClient feedbackClient = new ElmUserFeedbackClient(uri, "geheim");
		feedbackClient.start();
		
		HomeServerManager hsManager = new HomeServerManager(hs, feedbackClient);
//		hsManager.setPollingIntervalMillis(5_000);
		hsManager.setLogLevel(Level.WARNING);
		
		Scheduler scheduler = new Scheduler(40_000);
		scheduler.setLogLevel(Level.WARNING);
		scheduler.setIsAliveCheckDisabled(true); // enable debugger
//		scheduler.setSchedulingIntervalMillis(5_000);
		scheduler.addHomeServer(hs);
		
		hsManager.start();
		scheduler.start();
	}

}
