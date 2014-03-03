package elm.scheduler;

import static elm.hs.api.model.ElmStatus.ERROR;
import static elm.hs.api.model.ElmStatus.OFF;
import static elm.hs.api.model.ElmStatus.ON;
import static elm.hs.api.model.ElmStatus.OVERLOAD;
import static elm.hs.api.model.ElmStatus.SATURATION;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import elm.hs.api.model.ElmStatus;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.DeviceController.DeviceStatus;
import elm.scheduler.model.HomeServer;

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

	/** The total amount of power being requested. */
	private int totalDemandPowerWatt;

	/** Enable deterministic testing via a replacement of this time service. */
	private ElmTimeService timeService = ElmTimeService.INSTANCE;

	private long overloadModeBeginTime = NOT_IN_OVERLOAD;

	private boolean isAliveCheckDisabled;

	private final DecimalFormat kWFormat;

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
		kWFormat = new DecimalFormat();
		kWFormat.setMinimumFractionDigits(1);
		kWFormat.setMaximumFractionDigits(1);
		log.info("saturation limit: " + formatPower(saturationPowerLimitWatt) + ", overload limit: " + formatPower(overloadPowerLimitWatt));
	}

	@Override
	protected void statusChanged(ElmStatus oldStatus, ElmStatus newStatus, String logMsg) {
		super.statusChanged(oldStatus, newStatus, logMsg);
		if (newStatus.in(OFF, ERROR) || newStatus == ON && oldStatus.in(OFF, ERROR)) {
			for (HomeServer server : homeServers) {
				for (DeviceController device : server.getDeviceControllers()) {
					device.updateUserFeedback(newStatus, 0);
					if (newStatus.in(OFF, ERROR)) {
						device.updateMaximumPowerConsumption(newStatus, DeviceController.NO_POWER);
					}
				}
			}
		}
	}

	/** Used for testing. */
	void setTimeService(ElmTimeService timeService) {
		assert timeService != null;
		this.timeService = timeService;
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

		if (totalDemandPowerWatt != this.totalDemandPowerWatt) {
			log.info("Total requested power: " + formatPower(totalDemandPowerWatt));
			this.totalDemandPowerWatt = totalDemandPowerWatt;
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
	 * Also used for testing.
	 */
	final boolean isInOverloadMode() {
		return overloadModeBeginTime != NOT_IN_OVERLOAD;
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
			overloadModeBeginTime = timeService.currentTimeMillis();
			log.info("Beginning overload mode");
		}
		// Sort devices in ascending order of consumption start time. Later we grant power to consuming devices in the order they started their consumption.
		// Devices with an approved consumption will not be preempted.
		sortByConsumptionStartTime(consumingDevices);

		int totalDemandPowerWatt = 0;
		int[] expectedWaitingTimeMillis = new int[] { 0 }; // no waiting time
		int waitingTimesIndex = 0;

		for (DeviceController device : consumingDevices) {
			if (device.getStatus() == DeviceStatus.CONSUMPTION_APPROVED || totalDemandPowerWatt + device.getDemandPowerWatt() <= overloadPowerLimitWatt) {
				// consumption approved
				totalDemandPowerWatt += device.getDemandPowerWatt();
				device.updateMaximumPowerConsumption(OVERLOAD, DeviceController.UNLIMITED_POWER);
				device.updateUserFeedback(OVERLOAD, 0);
			} else {
				// must wait for one or more devices to finish, depending on its position in the sorted list.
				if (waitingTimesIndex == 0) {
					expectedWaitingTimeMillis = getExpectedWaitingDelayMillis(consumingDevices);
				}
				device.updateMaximumPowerConsumption(OVERLOAD, DeviceController.NO_POWER);
				device.updateUserFeedback(OVERLOAD, expectedWaitingTimeMillis[waitingTimesIndex]);
				waitingTimesIndex++;
			}
		}
		if (totalDemandPowerWatt > overloadPowerLimitWatt) {
			log.severe("Overload power limit (" + formatPower(overloadPowerLimitWatt) + ") overrun: " + formatPower(totalDemandPowerWatt));
			// TODO should reduce the water flow of all consuming devices
		}

		for (DeviceController device : standbyDevices) {
			device.updateMaximumPowerConsumption(OVERLOAD, DeviceController.NO_POWER);
			device.updateUserFeedback(OVERLOAD, expectedWaitingTimeMillis[waitingTimesIndex]); // same expected time for all standby devices
		}

		for (HomeServer server : homeServers) {
			server.fireDeviceUpdatesPending();
		}
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
			overloadModeBeginTime = NOT_IN_OVERLOAD;
			log.info("Ending overload mode after " + (timeService.currentTimeMillis() - overloadModeBeginTime) + " ms");
			// Notify consuming devices first as there may be some that had the Power level reduced earlier
			updateDevices(newStatus, consumingDevices, standbyDevices, false);

		} else if (newStatus != oldStatus) {
			updateDevices(newStatus, consumingDevices, standbyDevices, false);
		} else {
			// confirm only started or ended consumptions:
			updateDevices(newStatus, consumingDevices, standbyDevices, true);
		}
	}

	private void updateDevices(ElmStatus newStatus, List<DeviceController> consumingDevices, List<DeviceController> standbyDevices,
			boolean updateOnlyTransitioning) {
		Set<HomeServer> affectedHomeServers = new HashSet<HomeServer>();
		List<DeviceController> allDevices = new ArrayList<DeviceController>(consumingDevices);
		allDevices.addAll(standbyDevices);
		for (DeviceController device : allDevices) {
			if (!updateOnlyTransitioning || device.getStatus().isTransitioning()) {
				device.updateMaximumPowerConsumption(newStatus, DeviceController.UNLIMITED_POWER);
				device.updateUserFeedback(newStatus, 0);
				affectedHomeServers.add(device.getHomeServer());
			}
		}
		for (HomeServer server : affectedHomeServers) {
			server.fireDeviceUpdatesPending();
		}
	}

	private final String formatPower(int totalDemandPowerWatt) {
		return kWFormat.format(totalDemandPowerWatt / 1000.0) + " kW";
	}

	private void sortByConsumptionStartTime(List<DeviceController> consumingDevices) {
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

	/**
	 * Returns a sorted list of expected waiting times in ascending order.
	 * 
	 * @param consumingDevices
	 *            must contain at least one element
	 * @return a list containing at least one element
	 */
	private int[] getExpectedWaitingDelayMillis(List<DeviceController> consumingDevices) {
		assert consumingDevices != null && consumingDevices.size() > 0;
		long time = timeService.currentTimeMillis();
		int[] result = new int[consumingDevices.size()];
		int i = 0;
		for (DeviceController device : consumingDevices) {
			final int rawMillis = (int) (device.getMeanConsumptionMillis() - (time - device.getConsumptionStartTime()));
			result[i++] = rawMillis >= 0 ? rawMillis : 0;
		}
		// sort in ascending order: the device that finishes first provides a slot for the next one to start
		Arrays.sort(result);
		return result;
	}
}
