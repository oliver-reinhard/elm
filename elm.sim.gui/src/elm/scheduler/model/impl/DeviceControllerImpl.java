package elm.scheduler.model.impl;

import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_ENDED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_LIMITED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceController.DeviceStatus.ERROR;
import static elm.scheduler.model.DeviceController.DeviceStatus.INITIALIZING;
import static elm.scheduler.model.DeviceController.DeviceStatus.NOT_CONNECTED;
import static elm.scheduler.model.DeviceController.DeviceStatus.READY;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.Scheduler;
import elm.scheduler.model.AsynchRemoteDeviceUpdate;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.UnsupportedModelException;
import elm.ui.api.ElmStatus;
import elm.ui.api.ElmUserFeedback;

/**
 * This device-controller implementation is close to <em>stateless</em> in that, each time it runs, it performs a full analysis of all locally stored
 * {@link Device} fields and {@link Scheduler} input. It keeps only minimal state and status information and thus depend only weakly on its previous state from
 * which it might never recover. </p>
 */
public class DeviceControllerImpl implements DeviceController {

	private static final int LOWEST_INTAKE_WATER_TEMPERATURE = 50;

	/** Changes in intake-water temperature (in 1/10°C) below this threshold are ignored. */
	private static final int INTAKE_WATER_TEMP_CHANGE_IGNORE_DELTA = 20;

	/** The minimum delay between ELM status notifications if no status change is involved. */
	private static final int ELM_STATUS_NOTIFICATION_MIN_DELAY_MILLIS = 5000;

	private static final Logger LOG = Logger.getLogger(DeviceControllerImpl.class.getName());

	private final String id;
	private final HomeServer homeServer;
	private final String name;
	private DeviceStatus status = INITIALIZING;
	private DeviceModel deviceModel;

	/** Model-dependent. */
	private short powerMaxUnits;

	private long consumptionStartTime = NO_CONSUMPTION;

	/** The power to consume demanded by the user. */
	private int demandPowerWatt = 0;

	/** The power to consume allowed by the scheduler, can be {@link #UNLIMITED_POWER} (value {@value #UNLIMITED_POWER}). */
	private int internalApprovedPowerWatt = UNLIMITED_POWER;

	/**
	 * The true reference temperature at the physical device [1/10°C] (aka setpoint). This demand temperature is changed by the scheduler while scald protection
	 * is effective. A value of {@value o#UNDEFINED_TEMPERATURE} means undefined.
	 */
	private short actualDemandTemperature = UNDEFINED_TEMPERATURE;

	/**
	 * Reference temperature [1/10°C] (aka setpoint) as last defined by the USER. This value is not changed by the scheduler and is used to replace the
	 * scheduler-set temperature later. A value of {@value #UNDEFINED_TEMPERATURE} means undefined.
	 */
	private short userDemandTemperature = UNDEFINED_TEMPERATURE;

	/** Temperature [1/10°C] set for scald protection. A value of {@value #UNDEFINED_TEMPERATURE} means scald protection is inactive. */
	private short scaldProtectionTemperature = UNDEFINED_TEMPERATURE;

	/** Temperature [1/10°C] of the device intake water. A value of {@value #UNDEFINED_TEMPERATURE} means undefined. */
	private short intakeWaterTemperature = UNDEFINED_TEMPERATURE;

	/** The last {@link ElmStatus} communicated to the physical device. */
	private ElmStatus lastElmStatus = null;

	/** Unix time of last {@link ElmStatus} communicated to the physical device. */
	private long lastElmStatusNotificationTime = 0;

	public DeviceControllerImpl(HomeServer server, Device device) throws UnsupportedModelException {
		this(server, device, null);
	}

	public DeviceControllerImpl(HomeServer server, Device device, String name) throws UnsupportedModelException {
		assert server != null;
		assert device != null;
		this.id = device.id;
		this.homeServer = server;
		this.name = (name == null || name.isEmpty()) ? device.id : name;

		deviceModel = DeviceModel.getModel(device);
		if (!deviceModel.getType().isRemoteControllable()) {
			setStatus(ERROR);
			throw new UnsupportedModelException(device.id);
		}
	}

	@Override
	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	@Override
	public HomeServer getHomeServer() {
		return homeServer;
	}

	@Override
	public DeviceModel getDeviceModel() {
		return deviceModel;
	}

	@Override
	public DeviceStatus getStatus() {
		return status;
	}

	/** Also used for testing. */
	void setStatus(DeviceStatus newStatus) {
		DeviceStatus oldStatus = status;
		if (oldStatus != newStatus) {
			status = newStatus;
			info("new status: " + newStatus);
		}
	}

	@Override
	public synchronized UpdateResult update(Device device) {
		assert device != null;
		assert device.info != null || device.status != null;

		// The Info block of the device only contains information about heater on/off. For the actual power value, the Status block is required,
		// however, the Status block must be requested individually for each device.
		if (device.status == null) {
			// device.info != null as per assertion, above
			final boolean deviceHeaterOn = device.info.flags == 0;
			// The heater is turned ON, or it is OFF because of denied or limited power or update() is called for the first time:
			if (deviceHeaterOn || status.isConsuming() || status == INITIALIZING) {
				if (status == INITIALIZING) {
					// if the scheduler died earlier while scald protection was active, then we have to remove it now 
					// (we may set it again immediately).
					AsynchRemoteDeviceUpdate update = new AsynchRemoteDeviceUpdate(this);
					update.clearScaldProtection(null);
					homeServer.putDeviceUpdate(update);
				}
				return UpdateResult.DEVICE_STATUS_REQUIRED;
			}
		}

		if (device.connected && status.in(INITIALIZING, NOT_CONNECTED)) {
			setStatus(READY);
		} else if (!device.connected && status != ERROR) {
			setStatus(NOT_CONNECTED);
		}

		UpdateResult result = UpdateResult.NO_UPDATES;

		if (device.status != null) {
			powerMaxUnits = device.status.powerMax;

			final int newDemandPowerWatt = toPowerWatt(device.status.power);
			if (newDemandPowerWatt != demandPowerWatt) {
				info("demand power change: " + newDemandPowerWatt / 1000 + " kW");
				if (demandPowerWatt == 0) {
					waterConsumptionStarted(newDemandPowerWatt);
				} else if (newDemandPowerWatt == 0) {
					waterConsumptionEnded();
				} else {
					waterConsumptionChanged(newDemandPowerWatt);
				}
				result = result.and(UpdateResult.URGENT_UPDATES);
			}

			actualDemandTemperature = device.status.setpoint;

			if (intakeWaterTemperature == UNDEFINED_TEMPERATURE || Math.abs(intakeWaterTemperature - device.status.tIn) > INTAKE_WATER_TEMP_CHANGE_IGNORE_DELTA) {
				intakeWaterTemperature = device.status.tIn;
				info("intake water temperature change: " + intakeWaterTemperature / 10 + "°C");
				result = result.and(status.isConsuming() ? UpdateResult.URGENT_UPDATES : UpdateResult.MINOR_UPDATES);
			}
		}
		return result;
	}

	/**
	 * Invocation prompted by the actual physical device.
	 * 
	 * @param demandPowerUnits
	 *            the power needed to satisfy the user demand (temperature, flow).
	 */
	void waterConsumptionStarted(int demandPowerWatt) {
		assert demandPowerWatt >= 0 && demandPowerWatt <= deviceModel.getPowerMaxWatt();
		this.demandPowerWatt = demandPowerWatt;
		consumptionStartTime = System.currentTimeMillis();
		setStatus(CONSUMPTION_STARTED); // requires approval by scheduler
	}

	/**
	 * Invocation prompted by the actual physical device.
	 */
	void waterConsumptionChanged(final int newDemandPowerWatt) {
		demandPowerWatt = newDemandPowerWatt;
	}

	/**
	 * Invocation prompted by the actual physical device.
	 */
	void waterConsumptionEnded() {
		demandPowerWatt = 0;
		consumptionStartTime = NO_CONSUMPTION;
		setStatus(DeviceStatus.CONSUMPTION_ENDED); // requires approval by scheduler
	}

	@Override
	public void updateUserFeedback(ElmStatus schedulerStatus) {
		info("feedback status == scheduler status: " + schedulerStatus);
		// propagate scheduler status as device status:
		getHomeServer().putDeviceUpdate(new AsynchRemoteDeviceUpdate(this, schedulerStatus));
	}

	@Override
	public synchronized void updateMaximumPowerConsumption(int approvedPowerWatt, ElmStatus elmStatus, int expectedWaitingTimeMillis) {
		if (LOG.isLoggable(Level.INFO)) {
			info("power consumption: demand " + demandPowerWatt / 1000 + " kW, approved "
					+ (approvedPowerWatt == UNLIMITED_POWER ? deviceModel.getPowerMaxWatt() : approvedPowerWatt) / 1000 + " kW, elm " + elmStatus);
		}
		if (status == NOT_CONNECTED) {
			return;
		}
		assert approvedPowerWatt >= 0 && approvedPowerWatt <= deviceModel.getPowerMaxWatt() || approvedPowerWatt == UNLIMITED_POWER;
		final int newApprovedPowerWatt = approvedPowerWatt == deviceModel.getPowerMaxWatt() ? UNLIMITED_POWER : approvedPowerWatt;

		if (internalApprovedPowerWatt != newApprovedPowerWatt || status.isTransitioning()) {

			AsynchRemoteDeviceUpdate deviceUpdate = new AsynchRemoteDeviceUpdate(this);
			if (internalApprovedPowerWatt != newApprovedPowerWatt) {
				setApprovedPowerWatt(newApprovedPowerWatt, deviceUpdate);
			}

			if (status.isConsuming()) {
				if (newApprovedPowerWatt == 0) {
					setStatus(CONSUMPTION_DENIED);
				} else if (newApprovedPowerWatt == UNLIMITED_POWER || newApprovedPowerWatt >= this.demandPowerWatt) {
					setStatus(CONSUMPTION_APPROVED);
				} else {
					setStatus(CONSUMPTION_LIMITED);
				}
			} else if (status == CONSUMPTION_ENDED) {
				setStatus(READY);
			}

			putElmStatus(deviceUpdate, status, elmStatus, expectedWaitingTimeMillis);
			if (!deviceUpdate.isVoid()) {
				getHomeServer().putDeviceUpdate(deviceUpdate);
			}
		}
	}

	void setApprovedPowerWatt(final int newApprovedPowerWatt, AsynchRemoteDeviceUpdate deviceUpdate) {
		internalApprovedPowerWatt = newApprovedPowerWatt;
		// scald protection
		if (internalApprovedPowerWatt == UNLIMITED_POWER) {
			scaldProtectionTemperature = UNDEFINED_TEMPERATURE;
			// restore user-defined demand temperature (ASYNCHRONOUS device update):
			deviceUpdate.clearScaldProtection(userDemandTemperature == UNDEFINED_TEMPERATURE ? actualDemandTemperature : userDemandTemperature);
			userDemandTemperature = UNDEFINED_TEMPERATURE;

		} else { // limited power
			scaldProtectionTemperature = toTemperatureLimit(newApprovedPowerWatt);
			assert scaldProtectionTemperature >= deviceModel.getScaldProtectionTemperatureMin();
			if (userDemandTemperature == UNDEFINED_TEMPERATURE) {
				userDemandTemperature = actualDemandTemperature;
			}
			// ASYNCHRONOUS device update:
			deviceUpdate.setScaldProtectionTemperature(scaldProtectionTemperature);
		}
	}

	/** Used for testing. */
	void putElmStatus(AsynchRemoteDeviceUpdate deviceUpdate, DeviceStatus currentStatus, ElmStatus schedulerStatus, int expectedWaitingTimeMillis) {
		ElmStatus deviceFeedbackStatus = null;
		switch (currentStatus) {
		case READY:
			deviceFeedbackStatus = schedulerStatus;
			break;
		case CONSUMPTION_STARTED:
		case CONSUMPTION_APPROVED:
			deviceFeedbackStatus = ElmStatus.ON;
			break;
		case CONSUMPTION_LIMITED:
			deviceFeedbackStatus = ElmStatus.SATURATION;
			break;
		case CONSUMPTION_DENIED:
			deviceFeedbackStatus = ElmStatus.OVERLOAD;
			break;
		case ERROR:
			deviceFeedbackStatus = ElmStatus.ERROR;
			break;
		case INITIALIZING:
		case NOT_CONNECTED:
		default:
			throw new IllegalArgumentException(currentStatus.toString());
		}
		final long time = System.currentTimeMillis();
		if (deviceFeedbackStatus != lastElmStatus
		// do not propagate status notifications too often:
				|| (expectedWaitingTimeMillis > 0 && (time - lastElmStatusNotificationTime) >= ELM_STATUS_NOTIFICATION_MIN_DELAY_MILLIS)) {
			ElmUserFeedback feedback = new ElmUserFeedback(id, deviceFeedbackStatus);
			feedback.schedulerStatus = schedulerStatus;
			feedback.expectedWaitingTimeMillis = expectedWaitingTimeMillis;

			deviceUpdate.setFeedback(feedback);
			lastElmStatus = deviceFeedbackStatus;
			lastElmStatusNotificationTime = time;

			info("feedback status " + deviceFeedbackStatus);
		}
	}

	/**
	 * @param approvedPowerWatt
	 * @return the temperature in [1/10°C] that requires the given amount of power
	 */
	short toTemperatureLimit(final int approvedPowerWatt) {
		if (getDemandPowerWatt() > 0) {
			// dPowerDemand [W] = flow [kg/sec] * dTDemand [°K] * heatCapacity [J/kg/K].
			// Assume flow and heatCapacity are constant =>
			// dTnew = dPowerApproved / (demandPower / dTDemand)
			int flowTimesHeatCapacity = getDemandPowerWatt() / (getDemandTemperature() - getIntakeWaterTemperature());
			short temperature = (short) (approvedPowerWatt / flowTimesHeatCapacity + getIntakeWaterTemperature());
			if (temperature < deviceModel.getScaldProtectionTemperatureMin()) {
				return deviceModel.getScaldProtectionTemperatureMin();
			}
			if (temperature > deviceModel.getScaldProtectionTemperatureMax()) {
				return deviceModel.getScaldProtectionTemperatureMax();
			}
			return temperature;

		} else {
			// Interpolate from maximum device heating power and maximum temperature difference:
			return (short) (deviceModel.getScaldProtectionTemperatureMin() + (deviceModel.getScaldProtectionTemperatureMax() - LOWEST_INTAKE_WATER_TEMPERATURE)
					* approvedPowerWatt / deviceModel.getPowerMaxWatt());
		}
	}

	@Override
	public long getConsumptionStartTime() {
		return consumptionStartTime;
	}

	@Override
	public int getDemandPowerWatt() {
		return demandPowerWatt;
	}

	@Override
	public int getApprovedPowerWatt() {
		return internalApprovedPowerWatt == UNLIMITED_POWER ? deviceModel.getPowerMaxWatt() : internalApprovedPowerWatt;
	}

	private short getDemandTemperature() {
		return userDemandTemperature == UNDEFINED_TEMPERATURE ? actualDemandTemperature : userDemandTemperature;
	}

	/** Used for testing. */
	public int getIntakeWaterTemperature() {
		return intakeWaterTemperature;
	}

	/**
	 * Used for testing.
	 * 
	 * @return value in [1/10°C]; returns {@link #UNDEFINED_TEMPERATURE} if scald protection is disabled
	 */
	public short getScaldProtectionTemperature() {
		return scaldProtectionTemperature;
	}

	/** Used for testing. */
	short getActualDemandTemperature() {
		return actualDemandTemperature;
	}

	/** Used for testing. */
	short getUserDemandTemperature() {
		return userDemandTemperature;
	}

	private int toPowerWatt(short powerUnits) {
		assert powerMaxUnits != 0;
		return deviceModel.getPowerMaxWatt() * powerUnits / powerMaxUnits;
	}

	@SuppressWarnings("unused")
	private short toPowerUnits(int powerWatt) {
		assert powerMaxUnits != 0;
		return (short) (powerWatt * powerMaxUnits / deviceModel.getPowerMaxWatt());
	}

	private void info(String message) {
		log(Level.INFO, message, null);
	}

	private void log(Level level, String message, Throwable ex) {
		LOG.log(level, "Device " + id + ": " + message, ex);
	}

	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder(getName());
		b.append("(");
		b.append(getStatus());
		if (getStatus().isConsuming()) {
			b.append(", start: ");
			b.append(new SimpleDateFormat().format(new Date(consumptionStartTime)));
			b.append(", approved power: ");
			b.append(getApprovedPowerWatt());
			b.append(" W, scald-protection: ");
			if (getScaldProtectionTemperature() == UNDEFINED_TEMPERATURE) {
				b.append("inactive");
			} else {
				b.append(getScaldProtectionTemperature() / 10 + "°C");
			}
		}
		b.append(")");
		return b.toString();
	}
}