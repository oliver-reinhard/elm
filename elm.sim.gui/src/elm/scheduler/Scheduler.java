package elm.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.SetPowerLimit;

/**
 * This class implements the ELM scheduler as a robust, event-based scheduling algorithm.
 * <p>
 * The scheduler is <em>stateless</em> in that, each time it runs, it performs a full analysis of all known {@link HomeServer}s and their devices. It does thus
 * not depend on a previous state from which it might never recover.
 * </p>
 * <p>
 * The scheduler is a {@link HomeServerChangeListener listener} to critical changes of {@link HomeServer} {@link DeviceInfo} which trigger a new scheduling
 * cycle.
 * </p>
 */
public class Scheduler implements Runnable, HomeServerChangeListener {

	private static final int SCHEDULING_INTERVAL_MILLIS = 1_000;
	/** The maximum power of any individual device managed by this scheduler. */
	private static final int MAX_DEVICE_POWER_WATT = 27_000;
	/** The absolute saturation power limit is a fraction (among other things this factor) of the maximum power limit. */
	private static final double SATURATION_POWER_FACTOR = 0.9;

	/** Above this limit the scheduler is in {@link ElmStatus#SATURATION} mode. */
	private final int saturationPowerLimitWatt;

	/** Above this limit the scheduler is in {@link ElmStatus#OVERLOAD} mode. */
	private final int overloadPowerLimitWatt;

	private ElmStatus status = ElmStatus.OFF;

	/** The Home Server servers (and their connected devices) managed by this scheduler. */
	private final List<HomeServer> homeServers = new ArrayList<HomeServer>();
	private List<SchedulerChangeListener> listeners = new ArrayList<SchedulerChangeListener>();

	/* Threading and thread communication. */
	private Thread runner;
	private boolean shouldStop;
	private boolean devicesUpdated;

	private final Logger log = Logger.getLogger(getClass().getName());

	/**
	 * @param maxElectricalPowerWatt
	 *            the maximum electrical power that all the devices managed by this scheduler, in [Watt]
	 */
	public Scheduler(int maxElectricalPowerWatt) {
		assert maxElectricalPowerWatt > MAX_DEVICE_POWER_WATT;
		this.overloadPowerLimitWatt = maxElectricalPowerWatt;
		this.saturationPowerLimitWatt = Math.min(maxElectricalPowerWatt - MAX_DEVICE_POWER_WATT, (int) SATURATION_POWER_FACTOR * maxElectricalPowerWatt);
	}

	public synchronized void start() {
		runner = new Thread(this, Scheduler.class.getSimpleName());
		shouldStop = false;
		runner.start();
	}

	public synchronized void stop() {
		shouldStop = true;
		this.notify(); // ends the "run()" loop
	}

	public synchronized void addHomeServer(HomeServer server) {
		if (!homeServers.contains(server)) {
			homeServers.add(server);
			server.addChangeListener(this);
		}
	}

	public synchronized void removeHomeServer(HomeServer server) {
		if (homeServers.remove(server)) {
			server.removeChangeListener(this);
		}
	}

	public ElmStatus getStatus() {
		return status;
	}

	private void setStatus(ElmStatus newStatus) {
		if (newStatus != status) {
			ElmStatus oldStatus = status;
			status = newStatus;
			for (SchedulerChangeListener listener : listeners) {
				listener.statusChanged(oldStatus, newStatus);
			}
			log.info("status change: " + oldStatus + " -> " + newStatus);
		}
	}

	@Override
	public synchronized void run() {
		setStatus(ElmStatus.ON);
		while (!shouldStop) {
			try {
				// non-urgent device updates are processed after at most SCHEDULING_INTERVAL_MILLIS:
				wait(SCHEDULING_INTERVAL_MILLIS);
				if (devicesUpdated) {
					devicesUpdated = false;
					processDevices();
				}
			} catch (InterruptedException e) {
				break; // => exit
			}
		}
		setStatus(ElmStatus.OFF);
	}

	/**
	 * <em>Note: </em>This method is invoked from inside a {@code synchronized} section. Do not invoke long-running or blocking operations.
	 */
	private void processDevices() {
		int totalDemandPowerWatt = 0;
		List<DeviceInfo> consumingDevices = new ArrayList<DeviceInfo>();
		List<DeviceInfo> standbyDevices = new ArrayList<DeviceInfo>();

		for (HomeServer server : homeServers) {
			if (server.isAlive()) {
				for (DeviceInfo device : server.getDevicesInfos()) {
					if (device.getDemandPowerWatt() > 0) {
						totalDemandPowerWatt += device.getDemandPowerWatt();
						consumingDevices.add(device);
					} else {
						standbyDevices.add(device);
					}
				}
			} else {
				// HomeServer not updated
				setStatus(ElmStatus.ERROR);
				// TODO handle partial failure here
				return;
			}
		}

		if (totalDemandPowerWatt <= saturationPowerLimitWatt) {
			setStatus(ElmStatus.ON);
		} else if (totalDemandPowerWatt <= overloadPowerLimitWatt) {
			setStatus(ElmStatus.SATURATION);
		} else {
			setStatus(ElmStatus.OVERLOAD);
			// Sort devices in ascending order of consumption start time. Later we grant power to consuming devices in the order they started their consumption.
			// This means that those device who have started the consumption earlier are less likely to be preempted.
			sort(consumingDevices);
			totalDemandPowerWatt = 0;
			for (DeviceInfo device : consumingDevices) {
				int actualPowerLimit = DeviceInfo.NO_POWER;
				if (totalDemandPowerWatt + device.getDemandPowerWatt() <= overloadPowerLimitWatt) {
					totalDemandPowerWatt += device.getDemandPowerWatt();
					actualPowerLimit = DeviceInfo.UNLIMITED_POWER;
				}
				updateDevice(device, actualPowerLimit);
			}
			for (DeviceInfo device : standbyDevices) {
				updateDevice(device, DeviceInfo.NO_POWER);
			}
			for (HomeServer server : homeServers) {
				server.fireDeviceChangesPending();
			}
		}
	}

	private void updateDevice(DeviceInfo device, int actualPowerLimit) {
		device.getHomeServer().putDeviceUpdate(new SetPowerLimit(device, actualPowerLimit));
	}

	protected void sort(List<DeviceInfo> consumingDevices) {
		Collections.sort(consumingDevices, new Comparator<DeviceInfo>() {
			@Override
			public int compare(DeviceInfo d1, DeviceInfo d2) {
				if (d1.getConsumptionStartTime() == d2.getConsumptionStartTime()) {
					// if two consumptions started at the same time, then we favor the one with the lower power consumption:
					return Integer.compare(d1.getDemandPowerWatt(), d2.getDemandPowerWatt());
				} else if (d1.getConsumptionStartTime() < d2.getConsumptionStartTime()) {
					return -1;
				} else {
					return 1;
				}
			}
		});
	}

	@Override
	public synchronized void deviceInfosUpdated(HomeServer server, boolean urgent) {
		devicesUpdated = true;
		if (urgent) {
			notify();
		}
	}

	@Override
	public void deviceUpdatesPending(HomeServer server, boolean urgent) {
		// ignore
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
