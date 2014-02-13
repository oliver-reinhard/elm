package elm.scheduler;

import static elm.ui.api.ElmStatus.ERROR;
import static elm.ui.api.ElmStatus.ON;
import static elm.ui.api.ElmStatus.OVERLOAD;
import static elm.ui.api.ElmStatus.SATURATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import elm.scheduler.model.DeviceManager;
import elm.scheduler.model.DeviceManager.DeviceStatus;
import elm.scheduler.model.HomeServer;
import elm.ui.api.ElmStatus;

/**
 * This scheduler implementation is <em>stateless</em> in that, each time it runs, it performs a full analysis of all known {@link HomeServer}s and their
 * devices. It does thus not depend on a previous state from which it might never recover. </p>
 */
public class Scheduler extends AbstractScheduler {

	private static final int TO_BE_DONE = 0;

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

	private boolean isAliveCheckDisabled;

	/**
	 * @param maxElectricalPowerWatt
	 *            the maximum total electrical power in [Watt] that all the devices managed by this scheduler can use at any given time
	 */
	public Scheduler(int maxElectricalPowerWatt) {
		this(maxElectricalPowerWatt, Math.min(maxElectricalPowerWatt - MAX_DEVICE_POWER_WATT, (int) (SATURATION_POWER_FACTOR * maxElectricalPowerWatt)));
	}

	/**
	 * 
	 * @param maxElectricalPowerWatt
	 *            the maximum total electrical power in [Watt] that all the devices managed by this scheduler can use at any given time
	 * @param saturationPowerLimitWatt
	 *            the total electrical power threshold <em>before</em> the scheduler enters {@link ElmStatus#SATURATION} state.
	 * 
	 */
	public Scheduler(int maxElectricalPowerWatt, int saturationPowerLimitWatt) {
		assert maxElectricalPowerWatt > MAX_DEVICE_POWER_WATT;
		assert saturationPowerLimitWatt < maxElectricalPowerWatt;
		overloadPowerLimitWatt = maxElectricalPowerWatt;
		this.saturationPowerLimitWatt = saturationPowerLimitWatt;
		log.info("saturation limit: " + saturationPowerLimitWatt + ", overload limit: " + overloadPowerLimitWatt);
	}

	public void setIsAliveCheckDisabled(boolean isAliveCheckDisabled) {
		this.isAliveCheckDisabled = isAliveCheckDisabled;
	}

	/**
	 * <em>Note: </em>This method is invoked from inside a {@code synchronized} section. Do not invoke long-running or blocking operations.
	 */
	protected void processDevices() {
		int totalDemandPowerWatt = 0;
		List<DeviceManager> consumingDevices = new ArrayList<DeviceManager>();
		List<DeviceManager> standbyDevices = new ArrayList<DeviceManager>();

		// Prepare:
		for (HomeServer server : homeServers) {
			if (server.isAlive() || isAliveCheckDisabled) {
				for (DeviceManager device : server.getDeviceManagers()) {
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
			confirmStartedConsumptions(consumingDevices);
		} else if (totalDemandPowerWatt <= overloadPowerLimitWatt) {
			setStatus(SATURATION);
			endOverloadMode(consumingDevices, standbyDevices);
			confirmStartedConsumptions(consumingDevices);
		} else {
			setStatus(OVERLOAD);
			beginOverloadMode(consumingDevices, standbyDevices);
		}
	}

	private void beginOverloadMode(List<DeviceManager> consumingDevices, List<DeviceManager> standbyDevices) {
		// Sort devices in ascending order of consumption start time. Later we grant power to consuming devices in the order they started their consumption.
		// Devices with an approved consumption will not be preempted.
		sort(consumingDevices);
		int expectedWaitingTimeMillis = TO_BE_DONE;
		int totalDemandPowerWatt = 0;
		final ElmStatus currentStatus = getStatus();
		for (DeviceManager device : consumingDevices) {
			int approvedPowerLimit = DeviceManager.NO_POWER;
			if (device.getStatus() == DeviceStatus.CONSUMPTION_APPROVED || totalDemandPowerWatt + device.getDemandPowerWatt() <= overloadPowerLimitWatt) {
				totalDemandPowerWatt += device.getDemandPowerWatt();
				approvedPowerLimit = DeviceManager.UNLIMITED_POWER;
			} else if (!isInOverloadMode()) {
				overloadModeBeginTime = System.currentTimeMillis();
				log.info("Beginning overload mode");
			}
			device.updateMaximumPowerConsumption(approvedPowerLimit, currentStatus, expectedWaitingTimeMillis);
		}
		if (totalDemandPowerWatt > overloadPowerLimitWatt) {
			log.severe("Overload power limit (" + overloadModeBeginTime + " W) overrun: " + totalDemandPowerWatt + " W");
			// TODO should reduce the water flow of all consuming devices
		}
		for (DeviceManager device : standbyDevices) {
			device.updateMaximumPowerConsumption(DeviceManager.NO_POWER, currentStatus, expectedWaitingTimeMillis);
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

	private void endOverloadMode(List<DeviceManager> consumingDevices, List<DeviceManager> standbyDevices) {
		if (isInOverloadMode()) {
			log.info("Ending overload mode after " + (System.currentTimeMillis() - overloadModeBeginTime) + " ms");
			// Notify consuming devices first as there may be some that had the Power level reduced earlier
			final ElmStatus currentStatus = getStatus();
			for (DeviceManager device : consumingDevices) {
				device.updateMaximumPowerConsumption(DeviceManager.UNLIMITED_POWER, currentStatus, 0);
			}
			for (DeviceManager device : standbyDevices) {
				device.updateMaximumPowerConsumption(DeviceManager.UNLIMITED_POWER, currentStatus, 0);
			}
			for (HomeServer server : homeServers) {
				server.fireDeviceChangesPending();
			}
			overloadModeBeginTime = NOT_IN_OVERLOAD;
		}
	}

	private void confirmStartedConsumptions(List<DeviceManager> consumingDevices) {
		final ElmStatus currentStatus = getStatus();
		for (DeviceManager device : consumingDevices) {
			if (device.getStatus() == DeviceStatus.CONSUMPTION_STARTED) {
				device.updateMaximumPowerConsumption(DeviceManager.UNLIMITED_POWER, currentStatus, 0);
			}
		}
	}

	protected void sort(List<DeviceManager> consumingDevices) {
		Collections.sort(consumingDevices, new Comparator<DeviceManager>() {
			@Override
			public int compare(DeviceManager d1, DeviceManager d2) {
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
}
