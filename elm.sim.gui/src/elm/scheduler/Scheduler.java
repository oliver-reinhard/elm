package elm.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.SetPowerLimit;
import static elm.scheduler.ElmStatus.*;

/**
 * This scheduler implementation is <em>stateless</em> in that, each time it runs, it performs a full analysis of all known {@link HomeServer}s and their
 * devices. It does thus not depend on a previous state from which it might never recover. </p>
 */
public class Scheduler extends AbstractScheduler {

	/** The maximum power of any individual device managed by this scheduler. */
	private static final int MAX_DEVICE_POWER_WATT = 27_000;
	
	/** The absolute saturation power limit is a fraction (among other things this factor) of the maximum power limit. */
	private static final double SATURATION_POWER_FACTOR = 0.9;
	
	private static final long NOT_IN_OVERLOAD = 0L;

	/** Above this limit the scheduler is in {@link ElmStatus#SATURATION} mode. */
	private final int saturationPowerLimitWatt;

	/** Above this limit the scheduler is in {@link ElmStatus#OVERLOAD} mode. */
	private final int overloadPowerLimitWatt;
	
	private long overloadModeBeginTime = NOT_IN_OVERLOAD;

	/**
	 * @param maxElectricalPowerWatt
	 *            the maximum electrical power that all the devices managed by this scheduler, in [Watt]
	 */
	public Scheduler(int maxElectricalPowerWatt) {
		assert maxElectricalPowerWatt > MAX_DEVICE_POWER_WATT;
		overloadPowerLimitWatt = maxElectricalPowerWatt;
		saturationPowerLimitWatt = Math.min(maxElectricalPowerWatt - MAX_DEVICE_POWER_WATT, (int) (SATURATION_POWER_FACTOR * maxElectricalPowerWatt));
		log.info("saturation limit: " + saturationPowerLimitWatt + ", overload limit: " + overloadPowerLimitWatt);
	}

	/**
	 * <em>Note: </em>This method is invoked from inside a {@code synchronized} section. Do not invoke long-running or blocking operations.
	 */
	protected void processDevices() {
		int totalDemandPowerWatt = 0;
		List<DeviceInfo> consumingDevices = new ArrayList<DeviceInfo>();
		List<DeviceInfo> standbyDevices = new ArrayList<DeviceInfo>();

		// Prepare:
		for (HomeServer server : homeServers) {
			if (server.isAlive()) {
				for (DeviceInfo device : server.getDeviceInfos()) {
					if (device.getDemandPowerWatt() > 0) {
						totalDemandPowerWatt += device.getDemandPowerWatt();
						consumingDevices.add(device);
					} else {
						standbyDevices.add(device);
					}
				}
			} else {
				// HomeServer not updated
				setStatus(ERROR, "HomeServer " + server.getName() + " is not alive");
				// TODO handle partial failure here
				return;
			}
		}

		// Analyze and act:
		if (totalDemandPowerWatt <= saturationPowerLimitWatt) {
			setStatus(ON);
			endOverloadMode(consumingDevices, standbyDevices);
		} else if (totalDemandPowerWatt <= overloadPowerLimitWatt) {
			setStatus(SATURATION);
			endOverloadMode(consumingDevices, standbyDevices);
		} else {
			setStatus(OVERLOAD);
			beginOverloadMode(consumingDevices, standbyDevices);
		}
	}

	private void beginOverloadMode(List<DeviceInfo> consumingDevices, List<DeviceInfo> standbyDevices) {
		// Sort devices in ascending order of consumption start time. Later we grant power to consuming devices in the order they started their consumption.
		// This means that those device who have started the consumption earlier are less likely to be preempted.
		sort(consumingDevices);
		int totalDemandPowerWatt = 0;
		for (DeviceInfo device : consumingDevices) {
			int approvedPowerLimit = DeviceInfo.NO_POWER;
			if (totalDemandPowerWatt + device.getDemandPowerWatt() <= overloadPowerLimitWatt) {
				totalDemandPowerWatt += device.getDemandPowerWatt();
				approvedPowerLimit = DeviceInfo.UNLIMITED_POWER;
			} else if (! isInOverloadMode()) {
				overloadModeBeginTime = System.currentTimeMillis();
				log.info("Beginning overload mode");
			}
			updateDevice(device, approvedPowerLimit);
		}
		for (DeviceInfo device : standbyDevices) {
			updateDevice(device, DeviceInfo.NO_POWER);
		}
		for (HomeServer server : homeServers) {
			server.fireDeviceChangesPending();
		}
	}

	/**
	 * Also used for testing.
	 */
	final boolean isInOverloadMode() {
		return overloadModeBeginTime != NOT_IN_OVERLOAD;
	}
	
	private void endOverloadMode(List<DeviceInfo> consumingDevices, List<DeviceInfo> standbyDevices) {
		if (isInOverloadMode()) {
			log.info("Ending overload mode after " + (System.currentTimeMillis() - overloadModeBeginTime) + " ms");
			// Notify consuming devices first as there may be some that had the Power level reduced earlier
			for (DeviceInfo device : consumingDevices) {
				updateDevice(device, DeviceInfo.UNLIMITED_POWER);
			}
			for (DeviceInfo device : standbyDevices) {
				updateDevice(device, DeviceInfo.UNLIMITED_POWER);
			}
			for (HomeServer server : homeServers) {
				server.fireDeviceChangesPending();
			}
			overloadModeBeginTime = NOT_IN_OVERLOAD;
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

	protected void updateDevice(DeviceInfo device, int actualPowerLimit) {
		device.getHomeServer().putDeviceUpdate(new SetPowerLimit(device, actualPowerLimit));
	}
}
