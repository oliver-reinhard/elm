package elm.scheduler;

import static elm.ui.api.ElmStatus.ERROR;
import static elm.ui.api.ElmStatus.OFF;
import static elm.ui.api.ElmStatus.ON;
import static elm.ui.api.ElmStatus.OVERLOAD;
import static elm.ui.api.ElmStatus.SATURATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import elm.scheduler.model.AsynchRemoteDeviceUpdate;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.DeviceController.DeviceStatus;
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

	@Override
	protected void statusChanged(ElmStatus oldStatus, ElmStatus newStatus, String logMsg) {
		super.statusChanged(oldStatus, newStatus, logMsg);
		if (newStatus.in(OFF, ERROR) || newStatus == ON && oldStatus.in(OFF, ERROR)) {
			for (HomeServer server : homeServers) {
				server.putDeviceUpdate(new AsynchRemoteDeviceUpdate(newStatus));
				server.fireDeviceChangesPending();
			}
		}
	}

	public void setIsAliveCheckDisabled(boolean isAliveCheckDisabled) {
		this.isAliveCheckDisabled = isAliveCheckDisabled;
	}

	/**
	 * <em>Note: </em>This method is invoked from inside a {@code synchronized} section. Do not invoke long-running or blocking operations.
	 */
	protected void processDevices() {
		int totalDemandPowerWatt = 0;
		List<DeviceController> consumingDevices = new ArrayList<DeviceController>();
		List<DeviceController> standbyDevices = new ArrayList<DeviceController>();

		// Prepare device information:
		for (HomeServer server : homeServers) {
			if (server.isAlive() || isAliveCheckDisabled) {
				for (DeviceController device : server.getDeviceControllers()) {
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

		// Analyze:
		ElmStatus nextStatus;
		if (totalDemandPowerWatt <= saturationPowerLimitWatt) {
			nextStatus = ON;
		} else if (totalDemandPowerWatt <= overloadPowerLimitWatt) {
			nextStatus = SATURATION;
		} else {
			nextStatus = OVERLOAD;
		}

		// Act:
		if (nextStatus == OVERLOAD) {
			beginOverloadMode(consumingDevices, standbyDevices);
		} else {
			handleNormalMode(consumingDevices, standbyDevices, nextStatus);
		}
	}

	/**
	 * Notifies all devices that ELM has entered the {@link ElmStatus#OVERLOAD} status.
	 * <p>
	 * Also used for testing.
	 * </p>
	 */
	void beginOverloadMode(List<DeviceController> consumingDevices, List<DeviceController> standbyDevices) {
		setStatus(OVERLOAD);
		if (!isInOverloadMode()) {
			overloadModeBeginTime = System.currentTimeMillis();
			log.info("Beginning overload mode");
		}
		// Sort devices in ascending order of consumption start time. Later we grant power to consuming devices in the order they started their consumption.
		// Devices with an approved consumption will not be preempted.
		sort(consumingDevices);
		int expectedWaitingTimeMillis = TO_BE_DONE;
		int totalDemandPowerWatt = 0;

		for (DeviceController device : consumingDevices) {
			int approvedPowerLimit = DeviceController.NO_POWER;
			if (device.getStatus() == DeviceStatus.CONSUMPTION_APPROVED || totalDemandPowerWatt + device.getDemandPowerWatt() <= overloadPowerLimitWatt) {
				totalDemandPowerWatt += device.getDemandPowerWatt();
				approvedPowerLimit = DeviceController.UNLIMITED_POWER;
			}
			device.updateMaximumPowerConsumption(approvedPowerLimit, OVERLOAD, expectedWaitingTimeMillis);
		}
		if (totalDemandPowerWatt > overloadPowerLimitWatt) {
			log.severe("Overload power limit (" + overloadPowerLimitWatt + " W) overrun: " + totalDemandPowerWatt + " W");
			// TODO should reduce the water flow of all consuming devices
		}

		for (DeviceController device : standbyDevices) {
			device.updateMaximumPowerConsumption(DeviceController.NO_POWER, OVERLOAD, expectedWaitingTimeMillis);
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

	/**
	 * Notifies all devices that {@link ElmStatus#OVERLOAD} status is over. Confirms started consumptions.
	 * <p>
	 * Also used for testing.
	 * </p>
	 */
	void handleNormalMode(List<DeviceController> consumingDevices, List<DeviceController> standbyDevices, ElmStatus newStatus) {
		ElmStatus oldStatus = getStatus();
		setStatus(newStatus);
		if (isInOverloadMode()) {
			try {
				log.info("Ending overload mode after " + (System.currentTimeMillis() - overloadModeBeginTime) + " ms");
				// Notify consuming devices first as there may be some that had the Power level reduced earlier
				for (DeviceController device : consumingDevices) {
					device.updateMaximumPowerConsumption(DeviceController.UNLIMITED_POWER, newStatus, 0);
				}
				for (DeviceController device : standbyDevices) {
					device.updateMaximumPowerConsumption(DeviceController.UNLIMITED_POWER, newStatus, 0);
				}
				for (HomeServer server : homeServers) {
					server.fireDeviceChangesPending();
				}
			} finally {
				overloadModeBeginTime = NOT_IN_OVERLOAD;
			}

		} else if (newStatus != oldStatus) {
			if (consumingDevices.isEmpty()) {
				// optimization: no consuming devices => notify all devices via their home servers:
				for (HomeServer server : homeServers) {
					server.putDeviceUpdate(new AsynchRemoteDeviceUpdate(newStatus));
					server.fireDeviceChangesPending();
				}
			} else {
				for (DeviceController device : consumingDevices) {
					device.updateMaximumPowerConsumption(DeviceController.UNLIMITED_POWER, newStatus, 0);
				}
				for (DeviceController device : standbyDevices) {
					if (device.getStatus().isTransitioning()) {
						device.updateMaximumPowerConsumption(DeviceController.UNLIMITED_POWER, newStatus, 0);
					} else {
						device.updateUserFeedback(newStatus);
					}
				}
				for (HomeServer server : homeServers) {
					server.fireDeviceChangesPending();
				}
			}
		} else {
			// confirm started or ended consumptions:
			Set<HomeServer> affectedHomeServers = new HashSet<HomeServer>();
			List<DeviceController> allDevices = new ArrayList<DeviceController>(consumingDevices);
			allDevices.addAll(standbyDevices);
			for (DeviceController device : allDevices) {
				if (device.getStatus().isTransitioning()) {
					device.updateMaximumPowerConsumption(DeviceController.UNLIMITED_POWER, newStatus, 0);
					affectedHomeServers.add(device.getHomeServer());
				}
			}
			for (HomeServer server : affectedHomeServers) {
				server.fireDeviceChangesPending();
			}
		}
	}

	private void sort(List<DeviceController> consumingDevices) {
		Collections.sort(consumingDevices, new Comparator<DeviceController>() {
			@Override
			public int compare(DeviceController d1, DeviceController d2) {
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
