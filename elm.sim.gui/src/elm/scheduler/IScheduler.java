package elm.scheduler;

public interface IScheduler {

	void start();

	void stop();

	void devicesUpdated(boolean urgent);

	ElmStatus getStatus();

}