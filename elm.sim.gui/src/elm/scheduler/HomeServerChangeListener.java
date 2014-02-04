package elm.scheduler;

import elm.scheduler.model.HomeServer;

public interface HomeServerChangeListener {

	void deviceInfosUpdated(HomeServer server, boolean urgent);
	
	void deviceUpdatesPending(HomeServer server, boolean urgent);

}