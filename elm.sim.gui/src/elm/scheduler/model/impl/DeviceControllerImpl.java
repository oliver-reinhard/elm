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
import elm.hs.api.model.ElmStatus;
import elm.scheduler.ElmTimeService;
import elm.scheduler.ElmScheduler;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.RemoteDeviceUpdate;
import elm.scheduler.model.UnsupportedDeviceModelException;

/**
 * This device-controller implementation is close to <em>stateless</em> in that, each time it runs, it performs a full analysis of all locally stored
 * {@link Device} fields and {@link ElmScheduler} input. It keeps only minimal state and status information and thus depend only weakly on its previous state
 * from which it might never recover. </p>
 */
public class DeviceControllerImpl implements DeviceController {

	private static final int LOWEST_INTAKE_WATER_TEMPERATURE = 50;

	/** Changes in intake-water temperature (in 1/10°C) below this threshold are ignored. */
	private static final int INTAKE_WATER_TEMP_CHANGE_IGNORE_DELTA = 20;

	private static final Logger LOG = Logger.getLogger(DeviceControllerImpl.class.getName());

	/** Device (setpoint) temperature (in 1/10°C) set when scald-protection is disabled at initialization time. */
	private static final short DEFAULT_SETPOINT_TEMPERATURE_UNITS = 380;

	private final String id;
	private final HomeServer homeServer;
	private final String name;
	private DeviceStatus status = INITIALIZING;
	private DeviceModel deviceModel;

	/** Model-dependent. */
	private short powerMaxUnits;

	/** Enable deterministic testing via a replacement of this time service. */
	private ElmTimeService timeService = ElmTimeService.INSTANCE;

	private long consumptionStartTime = NO_CONSUMPTION;

	/** The power to consume demanded by the user. */
	private int demandPowerWatt = 0;

	/** The power to consume allowed by the scheduler, can be {@link #UNLIMITED_POWER} (value {@value #UNLIMITED_POWER}). */
	private int internalApprovedPowerWatt = UNLIMITED_POWER;

	/**
	 * The true reference temperature at the physical device [1/10°C] (aka setpoint). This demand temperature is changed by the scheduler while scald protection
	 * is effective. A value of {@value o#UNDEFINED_TEMPERATURE} means undefined.
	 */
	private short actualDemandTemperatureUnits = UNDEFINED_TEMPERATURE;

	/**
	 * Reference temperature [1/10°C] (aka setpoint) as last defined by the USER. This value is not changed by the scheduler and is used to replace the
	 * scheduler-set temperature later. A value of {@value #UNDEFINED_TEMPERATURE} means undefined.
	 */
	private short userDemandTemperatureUnits = UNDEFINED_TEMPERATURE;

	/** Temperature [1/10°C] set for scald protection. A value of {@value #UNDEFINED_TEMPERATURE} means scald protection is inactive. */
	private short scaldProtectionTemperatureUnits = UNDEFINED_TEMPERATURE;

	/** Temperature [1/10°C] of the device intake water. A value of {@value #UNDEFINED_TEMPERATURE} means undefined. */
	private short intakeWaterTemperatureUnits = UNDEFINED_TEMPERATURE;

	/** The {@link ElmStatus} last communicated to the physical device. */
	private ElmStatus lastDeviceStatus;

	/** The waiting time last communicated to the physical device. */
	private int lastWaitingTimeMillis;

	public DeviceControllerImpl(HomeServer server, Device device) throws UnsupportedDeviceModelException {
		this(server, device, null);
	}

	public DeviceControllerImpl(HomeServer server, Device device, String name) throws UnsupportedDeviceModelException {
		assert server != null;
		assert device != null;
		this.id = device.id;
		this.homeServer = server;
		this.name = (name == null || name.isEmpty()) ? device.id : name;

		deviceModel = DeviceModel.getModel(device);
		if (!deviceModel.getType().isRemoteControllable()) {
			setStatus(ERROR);
			throw new UnsupportedDeviceModelException(device.id);
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

	public void setTimeService(ElmTimeService timeService) {
		assert timeService != null;
		this.timeService = timeService;
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
			//
			// NOTE: flags == 0 even if power has been limited and heater is off due to enabled scald protection !
			//
			final boolean deviceHeaterOn = device.info.flags == 0;
			// The heater is turned ON 
			// - or it has been turned off but we are still consuming and need to stop it
			// - or update() is called for the first time.
			if (deviceHeaterOn || status.isConsuming() || status == INITIALIZING) {
				if (status == INITIALIZING) {
					// if the scheduler died earlier while scald protection was active, then we have to remove it now
					// (we may set it again immediately).
					RemoteDeviceUpdate update = new RemoteDeviceUpdate(this.id);
					update.clearScaldProtection(device.info.setpoint > deviceModel.getTemperatureOff() ? device.info.setpoint : DEFAULT_SETPOINT_TEMPERATURE_UNITS);
					homeServer.putDeviceUpdate(update);
				}
				return UpdateResult.DEVICE_STATUS_REQUIRED;
			}
		}

		if (device.connected && status.in(INITIALIZING, NOT_CONNECTED)) {
			setStatus(READY);
			homeServer.dispatchElmUserFeedback(id, ElmStatus.ON, 0);
		} else if (!device.connected && status != ERROR) {
			setStatus(NOT_CONNECTED);
		}

		UpdateResult result = UpdateResult.NO_UPDATES;

		if (device.status != null) {
			powerMaxUnits = device.status.powerMax;
			actualDemandTemperatureUnits = device.status.setpoint; // if power has been limited, this is not the user-defined setpoint !

			//
			// NOTE: flags == 0 even if power has been limited and heater is off due to enabled scald protection !
			//
			final boolean deviceHeaterOn = device.status.flags == 0;

			final int newDemandPowerWatt = toPowerWatt(device.status.power); // if power has been limited, this can be 0 even if user WANTS hot water

			if (deviceHeaterOn && !status.isConsuming()) {
				info("consumption started: " + newDemandPowerWatt / 1000 + " kW");
				waterConsumptionStarted(newDemandPowerWatt);
				result = result.and(UpdateResult.URGENT_UPDATES);

			} else if (deviceHeaterOn && status.isConsuming() && newDemandPowerWatt != demandPowerWatt) {
				info("demand power change: " + newDemandPowerWatt / 1000 + " kW");
				waterConsumptionChanged(newDemandPowerWatt);
				result = result.and(UpdateResult.URGENT_UPDATES);

			} else if (!deviceHeaterOn && status.isConsuming()) {
				info("consumption ended");
				waterConsumptionEnded();
				result = result.and(UpdateResult.URGENT_UPDATES);

			} // else no state change

			if (intakeWaterTemperatureUnits == UNDEFINED_TEMPERATURE || Math.abs(intakeWaterTemperatureUnits - device.status.tIn) > INTAKE_WATER_TEMP_CHANGE_IGNORE_DELTA) {
				intakeWaterTemperatureUnits = device.status.tIn;
				info("intake water temperature change: " + intakeWaterTemperatureUnits / 10 + "°C");
				result = result.and(status.isConsuming() ? UpdateResult.URGENT_UPDATES : UpdateResult.MINOR_UPDATES);
			}
		}
		return result;
	}

	/**
	 * Invocation prompted by the actual physical device.
	 * 
	 * @param demandPowerWatt
	 *            the power needed to satisfy the user demand (temperature, flow)
	 */
	void waterConsumptionStarted(int demandPowerWatt) {
		assert demandPowerWatt >= 0 && demandPowerWatt <= deviceModel.getPowerMaxWatt();
		this.demandPowerWatt = demandPowerWatt;
		consumptionStartTime = timeService.currentTimeMillis();
		setStatus(CONSUMPTION_STARTED); // requires approval by scheduler
	}

	/**
	 * Invocation prompted by the actual physical device.
	 * 
	 * @param newDemandPowerWatt
	 *            the changed power needed to satisfy the user demand (temperature, flow)
	 */
	void waterConsumptionChanged(final int newDemandPowerWatt) {
		this.demandPowerWatt = newDemandPowerWatt;
	}

	/**
	 * Invocation prompted by the actual physical device.
	 */
	void waterConsumptionEnded() {
		demandPowerWatt = 0;
		this.userDemandTemperatureUnits = UNDEFINED_TEMPERATURE;
		consumptionStartTime = NO_CONSUMPTION;
		setStatus(DeviceStatus.CONSUMPTION_ENDED); // requires approval by scheduler
	}

	@Override
	public synchronized void updateMaximumPowerConsumption(ElmStatus schedulerStatus, int approvedPowerWatt) {
		if (LOG.isLoggable(Level.INFO)) {
			info("power consumption: demand " + demandPowerWatt / 1000 + " kW, approved "
					+ (approvedPowerWatt == UNLIMITED_POWER ? deviceModel.getPowerMaxWatt() : approvedPowerWatt) / 1000 + " kW, ELM " + schedulerStatus);
		}
		if (status == NOT_CONNECTED) {
			return;
		}
		assert approvedPowerWatt >= 0 && approvedPowerWatt <= deviceModel.getPowerMaxWatt() || approvedPowerWatt == UNLIMITED_POWER;
		final int newApprovedPowerWatt = approvedPowerWatt == deviceModel.getPowerMaxWatt() ? UNLIMITED_POWER : approvedPowerWatt;

		if (internalApprovedPowerWatt != newApprovedPowerWatt || status.isTransitioning()) {

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

			if (internalApprovedPowerWatt != newApprovedPowerWatt) {
				internalApprovedPowerWatt = newApprovedPowerWatt;
				RemoteDeviceUpdate deviceUpdate = new RemoteDeviceUpdate(this.id);
				// scald protection disablement / enablement
				if (internalApprovedPowerWatt == UNLIMITED_POWER) {
					scaldProtectionTemperatureUnits = UNDEFINED_TEMPERATURE;
					// restore user-defined demand temperature (ASYNCHRONOUS device update):
					assert getDemandTemperatureUnits() >= deviceModel.getTemperatureOff();
					deviceUpdate.clearScaldProtection(getDemandTemperatureUnits());
					userDemandTemperatureUnits = UNDEFINED_TEMPERATURE;

				} else { // limited power
					scaldProtectionTemperatureUnits = toTemperatureLimit(newApprovedPowerWatt);
					assert scaldProtectionTemperatureUnits >= deviceModel.getTemperatureOff();
					if (userDemandTemperatureUnits == UNDEFINED_TEMPERATURE) {
						userDemandTemperatureUnits = actualDemandTemperatureUnits;
					}
					deviceUpdate.setScaldProtectionTemperature(scaldProtectionTemperatureUnits);
				}
				getHomeServer().putDeviceUpdate(deviceUpdate);
			}
		}
	}

	@Override
	public void updateUserFeedback(ElmStatus schedulerStatus, int expectedWaitingTimeMillis) {
		assert schedulerStatus != null;
		ElmStatus deviceFeedbackStatus = null;
		switch (status) {
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
			throw new IllegalArgumentException(status.toString());
		}
		if (deviceFeedbackStatus != lastDeviceStatus || expectedWaitingTimeMillis != lastWaitingTimeMillis) {
			lastDeviceStatus = deviceFeedbackStatus;
			lastWaitingTimeMillis = expectedWaitingTimeMillis;

			if (LOG.isLoggable(Level.INFO)) {
				info("feedback status " + deviceFeedbackStatus + ", waiting time " + expectedWaitingTimeMillis + " ms");
			}
			getHomeServer().dispatchElmUserFeedback(id, deviceFeedbackStatus, expectedWaitingTimeMillis);
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
			int flowTimesHeatCapacity = getDemandPowerWatt() / (getDemandTemperatureUnits() - getIntakeWaterTemperatureUnits());
			short temperature = (short) (approvedPowerWatt / flowTimesHeatCapacity + getIntakeWaterTemperatureUnits());
			if (temperature < deviceModel.getTemperatureOff()) {
				return deviceModel.getTemperatureOff();
			}
			if (temperature > deviceModel.getTemperatureMax()) {
				return deviceModel.getTemperatureMax();
			}
			return temperature;

		} else {
			// Interpolate from maximum device heating power and maximum temperature difference:
			return (short) (deviceModel.getTemperatureOff() + (deviceModel.getTemperatureMax() - LOWEST_INTAKE_WATER_TEMPERATURE) * approvedPowerWatt
					/ deviceModel.getPowerMaxWatt());
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

	private short getDemandTemperatureUnits() {
		return userDemandTemperatureUnits == UNDEFINED_TEMPERATURE ? actualDemandTemperatureUnits : userDemandTemperatureUnits;
	}

	/** Used for testing. */
	public int getIntakeWaterTemperatureUnits() {
		return intakeWaterTemperatureUnits;
	}

	/**
	 * Used for testing.
	 * 
	 * @return value in [1/10°C]; returns {@link #UNDEFINED_TEMPERATURE} if scald protection is disabled
	 */
	public short getScaldProtectionTemperatureUnits() {
		return scaldProtectionTemperatureUnits;
	}

	/** Used for testing. */
	short getActualDemandTemperatureUnits() {
		return actualDemandTemperatureUnits;
	}

	/** Used for testing. */
	short getUserDemandTemperatureUnits() {
		return userDemandTemperatureUnits;
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
			if (getScaldProtectionTemperatureUnits() == UNDEFINED_TEMPERATURE) {
				b.append("inactive");
			} else {
				b.append(getScaldProtectionTemperatureUnits() / 10 + "°C");
			}
		}
		b.append(")");
		return b.toString();
	}

	@Override
	public int getMeanConsumptionMillis() {
		return 10_000; // TODO base on past consumptions
	}
}
