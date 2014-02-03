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
 */
public class Scheduler implements Runnable, IScheduler {

	private static final int SCHEDULING_INTERVAL_MILLIS = 1000;
	private static final int MAX_DEVICE_POWER_WATT = 27000;
	private static final double SATURATION_POWER_FACTOR = 0.9;

	private class SetPowerLimit extends DeviceUpdate {
		final int actualPowerLimitWatt;

		public SetPowerLimit(DeviceInfo device, int actualPowerLimit) {
			super(device);
			this.actualPowerLimitWatt = actualPowerLimit;
		}

		@Override
		public void run(HomeServerInternalApiClient client) throws ClientException{
			int actualValue = DeviceInfo.NO_POWER_LIMIT;
			// TODO set power limit
			// actualValue = client.setScaldProtectionTemperature(device.getId(), actualPowerLimitWatt);
			device.setActualPowerWatt(actualValue);
			log.info("Device " + device.getId() + ": set actual power limit to" + actualPowerLimitWatt + "[W]");
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

	@Override
	public synchronized void start() {
		runner = new Thread(this, Scheduler.class.getSimpleName());
		shouldStop = false;
		runner.start();
	}

	@Override
	public synchronized void stop() {
		shouldStop = true;
		this.notify(); // ends the "run()" loop
	}

	@Override
	public synchronized void devicesUpdated(boolean urgent) {
		devicesUpdated = true;
		if (urgent) {
			notify();
		}
	}

	@Override
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
		}
	}

	protected void sort(List<DeviceInfo> consumingDevices) {
		Collections.sort(consumingDevices, new Comparator<DeviceInfo>() {
			@Override
			public int compare(DeviceInfo d1, DeviceInfo d2) {
				if (d1.getConsumptionStartTime() == d2.getConsumptionStartTime()) {
					// if two consumptions started at the same time, then we favour the one with the lower power consumption:
					return Integer.compare(d1.getDemandPowerWatt(), d2.getDemandPowerWatt());
				} else if (d1.getConsumptionStartTime() < d2.getConsumptionStartTime()) {
					return -1;
				} else {
					return 1;
				}
			}
		});
	}

}
