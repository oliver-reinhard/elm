package elm.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.model.Device;
import elm.hs.api.model.ElmStatus;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.HomeServerChangeListener;

/**
 * This class is the base for event-based ELM schedulers, i.e. the scheduler re-acts to external stimuli. However, it uses its own {@link Thread} to be
 * independent of long-running or blocking calls.
 * <p>
 * This <em>abstract</em> scheduler implementation provides the infrastructure for the actual scheduling algorithm to run in and to communicate with the managed
 * {@link HomeServer}s. This class also enables the implementation of multiple concrete schedulers that follow different scheduling approaches.
 * </p>
 * <p>
 * The scheduler is a {@link HomeServerChangeListener listener} to critical changes of {@link HomeServer} {@link DeviceController} which trigger a new
 * scheduling cycle.
 * </p>
 */
public abstract class AbstractScheduler implements HomeServerChangeListener {

	/** Default scheduler cycle interval after which the scheduler checks for non-urgent device updates: {@value #SCHEDULING_INTERVAL_MILLIS_DEFAULT} */
	public static final int SCHEDULING_INTERVAL_MILLIS_DEFAULT = 1_000;

	private ElmStatus status = ElmStatus.OFF;
	/** The Home Server servers (and their connected devices) managed by this scheduler. */

	protected final List<HomeServer> homeServers = new ArrayList<HomeServer>();
	protected List<SchedulerChangeListener> listeners = new ArrayList<SchedulerChangeListener>();

	// Threading and thread communication:
	private Thread eventProcessor;
	private boolean shouldStop;

	/** The state of one or more {@link Device}s has changed. */
	private boolean devicesUpdated;
	private int schedulingRunCount;

	private int schedulingIntervalMillis = SCHEDULING_INTERVAL_MILLIS_DEFAULT;

	protected final Logger log = Logger.getLogger(getClass().getName());

	public synchronized void start() {
		eventProcessor = new Thread(new Runnable() { // don't expose run() by making the AbstractScheduler a Runnable

					@Override
					public void run() {
						eventLoop();
					}

				}, getClass().getSimpleName());
		shouldStop = false;
		eventProcessor.start();
	}

	public synchronized void stop() {
		shouldStop = true;
		this.notify(); // ends the "run()" loop
	}

	/**
	 * Scheduler cycle interval after which the scheduler checks for non-urgent device updates.
	 * 
	 * @return defaults to {@link #SCHEDULING_INTERVAL_MILLIS_DEFAULT}.
	 */
	public int getSchedulingIntervalMillis() {
		return schedulingIntervalMillis;
	}

	public void setSchedulingIntervalMillis(int schedulingIntervalMillis) {
		this.schedulingIntervalMillis = schedulingIntervalMillis;
	}

	/**
	 * Used for testing.
	 */
	List<HomeServer> getHomeServers() {
		return homeServers;
	}

	/**
	 * Used for testing.
	 */
	int getSchdedulingRunCount() {
		return schedulingRunCount;
	}

	public synchronized void addHomeServer(HomeServer server) {
		for (HomeServer hs : homeServers) {
			if (hs.getUri().equals(server)) {
				return;
			}
		}
		homeServers.add(server);
		server.addChangeListener(this);
	}

	public synchronized void removeHomeServer(HomeServer server) {
		if (homeServers.remove(server)) {
			server.removeChangeListener(this);
		}
	}

	public ElmStatus getStatus() {
		return status;
	}

	protected void setStatus(ElmStatus newStatus) {
		setStatus(newStatus, null);
	}

	protected void setStatus(ElmStatus newStatus, String logMsg) {
		final ElmStatus oldStatus = status;
		if (newStatus != oldStatus) {
			status = newStatus;
			statusChanged(oldStatus, newStatus, logMsg);
		}
	}

	protected void statusChanged(ElmStatus oldStatus, ElmStatus newStatus, String logMsg) {
		for (SchedulerChangeListener listener : listeners) {
			listener.statusChanged(oldStatus, newStatus);
		}
		if (log.isLoggable(Level.INFO)) {
			String txt = "status change: " + oldStatus + " -> " + newStatus;
			if (logMsg != null && !logMsg.isEmpty()) {
				txt = txt + " (" + logMsg + ")";
			}
			log.info(txt);
		}
	}

	/**
	 * This method is invoked by a dedicated event-processor {@link Thread}.
	 */
	protected synchronized void eventLoop() {
		try {
			setStatus(ElmStatus.ON);
			while (!shouldStop) {
				try {
					if (devicesUpdated) {
						devicesUpdated = false;
						schedulingRunCount++;
						processDevices();
					}
					// non-urgent device updates are processed after at most SCHEDULING_INTERVAL_MILLIS:
					log.log(Level.FINE, "wait " + schedulingIntervalMillis + " ms");
					wait(schedulingIntervalMillis);

				} catch (InterruptedException e) {
					break; // => exit
				}
			}
		} finally {
			setStatus(ElmStatus.OFF);
		}
	}

	/**
	 * Used for testing.
	 */
	void runOnce() {
		if (status == ElmStatus.OFF) {
			setStatus(ElmStatus.ON);
		}
		schedulingRunCount++;
		processDevices();
	}

	/**
	 * The core scheduling method.
	 * <p>
	 * <em>Note: </em>This method is invoked from inside a {@code synchronized} section. Do not invoke long-running or blocking operations a any price.
	 * </p>
	 */
	protected abstract void processDevices();

	@Override
	public synchronized void devicesControllersUpdated(HomeServer server, boolean urgent) {
		devicesUpdated = true;
		if (urgent) {
			notify();
		}
	}

	@Override
	public void deviceUpdatesPending(HomeServer server) {
		// ignore these notifications
	}

	public void addChangeListener(SchedulerChangeListener listener) {
		assert listener != null;
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeChangeListener(SchedulerChangeListener listener) {
		listeners.remove(listener);
	}

}