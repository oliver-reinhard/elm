package elm.scheduler.model.impl;

import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_ENDED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_LIMITED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceController.DeviceStatus.DENIED;
import static elm.scheduler.model.DeviceController.DeviceStatus.ERROR;
import static elm.scheduler.model.DeviceController.DeviceStatus.INITIALIZING;
import static elm.scheduler.model.DeviceController.DeviceStatus.NOT_CONNECTED;
import static elm.scheduler.model.DeviceController.DeviceStatus.READY;
import static elm.util.ElmLogFormatter.formatPower;
import static elm.util.ElmLogFormatter.formatTemperature;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.hs.api.model.ElmStatus;
import elm.scheduler.ElmScheduler;
import elm.scheduler.ElmTimeService;
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

	/** Device (setpoint) temperature (in 1/10°C) set if device setpoint is too low at initialization time. */
	private static final short DEFAULT_SETPOINT_TEMPERATURE_UNITS = 380;

	/** Changes in intake-water temperature (in 1/10°C) below this threshold are ignored. */
	private static final int TEMP_CHANGE_IGNORE_DELTA_UNITS = 20;

	/** Changes in flow (in 1/10 litre) below this threshold are ignored. */
	private static final int FLOW_CHANGE_IGNORE_DELTA_UNITS = 5;

	/** Joule per gram and Kelvin. */
	private static final double WATER_HEAT_CAPACITY = 4.192;

	private static final Logger LOG = Logger.getLogger(DeviceControllerImpl.class.getName());

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

	/** The true flow at the physical device [1/10 litre]. */
	private short deviceFlowUnits = 0;

	/** The true reference temperature [1/10°C] at the physical device. */
	private short deviceReferenceTemperatureUnits = 0;

	/**
	 * The reference temperature [1/10°C] (aka setpoint) as last defined by the USER. This value is not changed by the scheduler and is used to replace the
	 * scheduler-set reference temperature at a later time. A value of {@value #UNDEFINED_TEMPERATURE} means undefined.
	 */
	private short userDemandTemperatureUnits = UNDEFINED_TEMPERATURE;

	/** The true temperature [1/10°C] of the intake water at the physical device. */
	private short deviceIntakeWaterTemperatureUnits = 0;

	/** The power consumed by the device <em>before</em> a possible power limitation This value does not change with the scald-protection temperature. */
	private int calculatedPowerWatt = 0;

	/** The power to consume allowed by the scheduler, can be {@link #UNLIMITED_POWER} (value {@value #UNLIMITED_POWER}). */
	private int internalApprovedPowerWatt = UNLIMITED_POWER;

	/** Temperature [1/10°C] set for scald protection. A value of {@value #UNDEFINED_TEMPERATURE} means scald protection is inactive. */
	private short scaldProtectionTemperatureUnits = UNDEFINED_TEMPERATURE;

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

		//
		// NOTE: flags == 0 even if power has been limited and heater is off due to enabled scald protection !
		//
		final boolean deviceHeaterOn = device._isHeaterOn();

		// The Info block of the device only contains information about heater on/off. For the actual power value, the Status block is required,
		// however, the Status block must be requested individually for each device.
		if (device.status == null) {
			// The heater is turned ON
			// - or it has been turned off but we are still consuming and need to stop it
			// - or update() is called for the first time.
			if (deviceHeaterOn || status.isConsuming() || status == INITIALIZING) {
				if (status == INITIALIZING) {
					final short initialDemandTemperatureUnits = device.info.setpoint >= deviceModel.getTemperatureOff() + TEMP_CHANGE_IGNORE_DELTA_UNITS ? device.info.setpoint
							: DEFAULT_SETPOINT_TEMPERATURE_UNITS;
					setUserDemandTemperatureUnits(initialDemandTemperatureUnits);

					// if the scheduler died earlier while scald protection was active, then we have to remove it now
					// (we may set it again in a short moment).
					RemoteDeviceUpdate update = new RemoteDeviceUpdate(this.id);
					update.clearScaldProtection(initialDemandTemperatureUnits);
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

			boolean potentialPowerChange = false;

			//
			// NOTE: if power has been limited, device.status.setpoint is NOT the user-defined setpoint !
			//
			deviceReferenceTemperatureUnits = device.status.setpoint;
			if (userDemandTemperatureUnits == UNDEFINED_TEMPERATURE
					|| Math.abs(userDemandTemperatureUnits - deviceReferenceTemperatureUnits) > TEMP_CHANGE_IGNORE_DELTA_UNITS) {
				potentialPowerChange = true;
			}

			if (deviceIntakeWaterTemperatureUnits == UNDEFINED_TEMPERATURE
					|| Math.abs(deviceIntakeWaterTemperatureUnits - device.status.tIn) > TEMP_CHANGE_IGNORE_DELTA_UNITS) {
				deviceIntakeWaterTemperatureUnits = device.status.tIn;
				potentialPowerChange = true;
				info("intake water temperature change: " + formatTemperature(deviceIntakeWaterTemperatureUnits));
			}

			final short newDeviceFlowUnits = device.status.flow;
			final short oldDeviceFlowUnits = deviceFlowUnits;
			if (oldDeviceFlowUnits == 0 || Math.abs(oldDeviceFlowUnits - newDeviceFlowUnits) > FLOW_CHANGE_IGNORE_DELTA_UNITS) {
				deviceFlowUnits = newDeviceFlowUnits;
				potentialPowerChange = true;
				info("flow change: " + newDeviceFlowUnits / 10 + " litres/min.");
			}

			//
			// NOTE: if power has been limited, device.status.power can be 0 even if user WANTS hot water
			//
			final int newDevicePowerWatt = toPowerWatt(device.status.power);

			// START
			if (newDeviceFlowUnits > 0 && oldDeviceFlowUnits == 0) {
				if (status != DENIED) {
					setUserDemandTemperatureUnits(deviceReferenceTemperatureUnits);
				}
				// else: userDemandTemperatureUnits has been set when DENIED was set
				waterConsumptionStarted(newDevicePowerWatt);
				result = result.and(UpdateResult.URGENT_UPDATES);

				// CHANGE POWER
			} else if (newDeviceFlowUnits > 0 && potentialPowerChange) {
				if (status == CONSUMPTION_APPROVED) {
					setUserDemandTemperatureUnits(deviceReferenceTemperatureUnits);
				}
				// else: deviceReferenceTemperatureUnits is NOT a user-set reference temperature
				powerConsumptionChanged(newDevicePowerWatt, "demand power change.");
				result = result.and(UpdateResult.URGENT_UPDATES);

				// END
			} else if (newDeviceFlowUnits == 0 && oldDeviceFlowUnits > 0) {
				waterConsumptionEnded();
				result = result.and(UpdateResult.URGENT_UPDATES);

			} // else no state change
		}
		return result;
	}

	/**
	 * Invocation prompted by the actual physical device.
	 * 
	 * @param devicePowerWatt
	 *            the power as provided by the device
	 */
	void waterConsumptionStarted(int devicePowerWatt) {
		powerConsumptionChanged(devicePowerWatt, "consumption started.");
		consumptionStartTime = timeService.currentTimeMillis();
		setStatus(CONSUMPTION_STARTED); // requires approval by scheduler
	}

	/**
	 * Invocation prompted by the actual physical device.
	 * 
	 * @param devicePowerWatt
	 *            the power as provided by the device
	 */
	void powerConsumptionChanged(int devicePowerWatt, String info) {
		assert devicePowerWatt >= 0 && devicePowerWatt <= deviceModel.getPowerMaxWatt();
		int newValue = calculatePowerWatt(deviceIntakeWaterTemperatureUnits, userDemandTemperatureUnits, deviceFlowUnits);
		if (newValue != calculatedPowerWatt) {
			calculatedPowerWatt = newValue;
			info(info + " Device: " + formatPower(devicePowerWatt) + ", calculated: " + formatPower(calculatedPowerWatt));
		}
	}

	/**
	 * Invocation prompted by the actual physical device.
	 */
	void waterConsumptionEnded() {
		info("consumption ended");
		calculatedPowerWatt = 0;
		consumptionStartTime = NO_CONSUMPTION;
		setStatus(DeviceStatus.CONSUMPTION_ENDED); // requires approval by scheduler
	}

	@Override
	public synchronized void updateMaximumPowerConsumption(ElmStatus schedulerStatus, int approvedPowerWatt) {
		assert approvedPowerWatt >= 0 && approvedPowerWatt <= deviceModel.getPowerMaxWatt() || approvedPowerWatt == UNLIMITED_POWER;

		if (status == NOT_CONNECTED) {
			return;
		}
		final int newApprovedPowerWatt = approvedPowerWatt == deviceModel.getPowerMaxWatt() ? UNLIMITED_POWER : approvedPowerWatt;

		if (internalApprovedPowerWatt != newApprovedPowerWatt || status.isTransitioning()) {
			
			if (LOG.isLoggable(Level.INFO)) {
				info("power consumption: demand " + formatPower(calculatedPowerWatt) + ", approved "
						+ formatPower(approvedPowerWatt == UNLIMITED_POWER ? deviceModel.getPowerMaxWatt() : approvedPowerWatt) + ", ELM " + schedulerStatus);
			}

			if (status.isConsuming()) {
				if (newApprovedPowerWatt == 0) {
					setStatus(CONSUMPTION_DENIED);
				} else if (newApprovedPowerWatt == UNLIMITED_POWER || newApprovedPowerWatt >= calculatedPowerWatt) {
					setStatus(CONSUMPTION_APPROVED);
				} else {
					setStatus(CONSUMPTION_LIMITED);
				}
			} else if (status == READY && newApprovedPowerWatt == NO_POWER) {
				setStatus(DENIED);
				// set last recorded reference temperature as reported by device
				setUserDemandTemperatureUnits(deviceReferenceTemperatureUnits);
			} else if (status == CONSUMPTION_ENDED || status == DENIED && newApprovedPowerWatt == UNLIMITED_POWER) {
				setStatus(READY);
			}

			if (internalApprovedPowerWatt != newApprovedPowerWatt) {
				internalApprovedPowerWatt = newApprovedPowerWatt;
				RemoteDeviceUpdate deviceUpdate = new RemoteDeviceUpdate(this.id);
				// scald protection disablement / enablement
				if (internalApprovedPowerWatt == UNLIMITED_POWER) {
					scaldProtectionTemperatureUnits = UNDEFINED_TEMPERATURE;
					// restore user-defined demand temperature (ASYNCHRONOUS device update):
					assert userDemandTemperatureUnits >= deviceModel.getTemperatureOff() : "device " + id + ", demand temperature = "
							+ userDemandTemperatureUnits;
					deviceUpdate.clearScaldProtection(userDemandTemperatureUnits);

				} else { // limited power
					scaldProtectionTemperatureUnits = toTemperatureLimitUnits(newApprovedPowerWatt);
					assert scaldProtectionTemperatureUnits >= deviceModel.getTemperatureOff();
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
		case DENIED:
			deviceFeedbackStatus = ElmStatus.OVERLOAD;
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
	short toTemperatureLimitUnits(final int approvedPowerWatt) {
		short temperature = (short) (approvedPowerWatt / (WATER_HEAT_CAPACITY * deviceFlowUnits * 100 / 60) * 10 + deviceIntakeWaterTemperatureUnits);
		if (temperature < deviceModel.getTemperatureOff()) {
			return deviceModel.getTemperatureOff();
		} else if (temperature > deviceModel.getTemperatureMax()) {
			return deviceModel.getTemperatureMax();
		}
		return temperature;
	}

	@Override
	public long getConsumptionStartTime() {
		return consumptionStartTime;
	}

	@Override
	public int getDemandPowerWatt() {
		return calculatedPowerWatt;
	}

	@Override
	public int getApprovedPowerWatt() {
		return internalApprovedPowerWatt == UNLIMITED_POWER ? deviceModel.getPowerMaxWatt() : internalApprovedPowerWatt;
	}

	/** Used for testing. */
	public int getIntakeWaterTemperatureUnits() {
		return deviceIntakeWaterTemperatureUnits;
	}

	/**
	 * Used for testing.
	 * 
	 * @return value in [1/10°C]; returns {@link #UNDEFINED_TEMPERATURE} if scald protection is disabled
	 */
	public short getScaldProtectionTemperatureUnits() {
		return scaldProtectionTemperatureUnits;
	}

	@Override
	public short getUserDemandTemperatureUnits() {
		return userDemandTemperatureUnits;
	}

	private void setUserDemandTemperatureUnits(short newValue) {
		if (newValue != userDemandTemperatureUnits) {
			info("reference temperature change (user): " + formatTemperature(newValue));
			userDemandTemperatureUnits = newValue;
		}
	}

	/**
	 * @param intakeTemperatureUnits
	 *            in 1/10°C
	 * @param demandTemperatureUnits
	 *            in 1/10°C
	 * @param flow
	 *            in 1/10 liter
	 * @return Watt
	 */
	private int calculatePowerWatt(short intakeTemperatureUnits, short demandTemperatureUnits, short flow) {
		if (demandTemperatureUnits <= deviceModel.getTemperatureOff() || demandTemperatureUnits <= intakeTemperatureUnits || flow == 0) {
			return 0;
		}
		int power = (int) (WATER_HEAT_CAPACITY * (demandTemperatureUnits - intakeTemperatureUnits) / 10 * flow * 100 / 60);
		if (power < deviceModel.getPowerMaxWatt()) {
			return power;
		}
		return deviceModel.getPowerMaxWatt();
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
			b.append(", demand: ");
			b.append(formatTemperature(getUserDemandTemperatureUnits()));
			b.append(", calculated: ");
			b.append(formatPower(getDemandPowerWatt()));
			b.append(", approved: ");
			b.append(formatPower(getApprovedPowerWatt()));
			b.append(", scald-protection: ");
			if (getScaldProtectionTemperatureUnits() == UNDEFINED_TEMPERATURE) {
				b.append("inactive");
			} else {
				b.append(formatTemperature(getScaldProtectionTemperatureUnits()));
			}
		}
		b.append(")");
		return b.toString();
	}

	@Override
	public int getMeanConsumptionMillis() {
		return 40_000; // TODO base on past consumptions
	}
}
