package elm.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.HomeServerInternalApiClient;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.DeviceUpdate;
import elm.scheduler.model.HomeServer;

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

	private static final int SCHEDULING_INTERVAL_MILLIS = 1000;
	private static final int MAX_DEVICE_POWER_WATT = 27000;
	private static final double SATURATION_POWER_FACTOR = 0.9;

	private class SetPowerLimit extends DeviceUpdate {
		final int actualPowerLimitWatt;

		public SetPowerLimit(DeviceInfo device, int actualPowerLimit) {
			super(device, true);
			this.actualPowerLimitWatt = actualPowerLimit;
		}

		@Override
		public void run(HomeServerInternalApiClient client) throws ClientException {
			int actualValue = DeviceInfo.NO_POWER_LIMIT;
			// TODO set power limit
			// actualValue = client.setScaldProtectionTemperature(device.getId(), actualPowerLimitWatt);
			getDevice().setActualPowerWatt(actualValue);
			log.info("Device " + getDevice().getId() + ": set actual power limit to" + actualPowerLimitWatt + "[W]");
		}
	}

	/** Above this limit the scheduler is in {@link ElmStatus#SATURATION} mode. */
	private final int saturationPowerLimitWatt;

	/** Above this limit the scheduler is in {@link ElmStatus#OVERLOAD} mode. */
	private final int overloadPowerLimitWatt;
	private final List<HomeServer> homeServers = new ArrayList<HomeServer>();
	private Thread runner;
	private boolean shouldStop;
	private boolean devicesUpdated;

	private ElmStatus status = ElmStatus.OFF;
	private final Logger log = Logger.getLogger(getClass().getName());

	public Scheduler(int maxPowerWatt) {
		assert maxPowerWatt > MAX_DEVICE_POWER_WATT;
		this.overloadPowerLimitWatt = maxPowerWatt;
		this.saturationPowerLimitWatt = Math.min(maxPowerWatt - MAX_DEVICE_POWER_WATT, (int) SATURATION_POWER_FACTOR * maxPowerWatt);
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
		log.info("state " + status + " -> " + newStatus);
		this.status = newStatus;
	}

	@Override
	public synchronized void run() {
		setStatus(ElmStatus.ON);
		while (!shouldStop) {
			try {
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
		int demandPowerWatt = 0;
		List<DeviceInfo> consumingDevices = new ArrayList<DeviceInfo>();
		for (HomeServer server : homeServers) {
			if (server.isAlive()) {
				for (DeviceInfo device : server.getDevicesInfos()) {
					if (device.getDemandPowerWatt() > 0) {
						demandPowerWatt += device.getDemandPowerWatt();
						consumingDevices.add(device);
					}
				}
			} else {
				// HomeServer not updated
				setStatus(ElmStatus.ERROR);
				// TODO handle partial failure here
				return;
			}
		}
		if (demandPowerWatt <= saturationPowerLimitWatt) {
			setStatus(ElmStatus.ON);
		} else if (demandPowerWatt <= overloadPowerLimitWatt) {
			setStatus(ElmStatus.SATURATION);
		} else {
			setStatus(ElmStatus.OVERLOAD);
			// Sort devices in ascending order of consumption start time:
			sort(consumingDevices);
			demandPowerWatt = 0;
			for (DeviceInfo device : consumingDevices) {
				int actualPowerLimit = 0;
				if (demandPowerWatt + device.getDemandPowerWatt() <= overloadPowerLimitWatt) {
					demandPowerWatt += device.getDemandPowerWatt();
					actualPowerLimit = DeviceInfo.NO_POWER_LIMIT;
				}
				device.getHomeServer().putDeviceUpdate(new SetPowerLimit(device, actualPowerLimit));
			}
			for (HomeServer server : homeServers) {
				server.fireDeviceChangesPending();
			}
		}
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
}
