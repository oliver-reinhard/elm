package elm.scheduler.model.impl;

import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_LIMITED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.ERROR;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.NOT_CONNECTED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.READY;

import java.text.SimpleDateFormat;
import java.util.Date;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.ElmStatus;
import elm.scheduler.model.ClearScaldProtection;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.SetScaldProtectionTemperature;
import elm.scheduler.model.UnsupportedModelException;

public class DeviceInfoImpl implements DeviceInfo {

	private static final int LOWEST_INTAKE_WATER_TEMPERATURE = 50;

	/** Changes in intake-water temperature (in 1/10°C) below this threshold are ignored. */
	private static final int INTAKE_WATER_TEMP_CHANGE_IGNORE_DELTA = 20;

	private final String id;
	private final HomeServer homeServer;
	private final String name;
	private DeviceStatus status;
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

	public DeviceInfoImpl(HomeServer server, Device device) throws UnsupportedModelException {
		this(server, device, null);
	}

	public DeviceInfoImpl(HomeServer server, Device device, String name) throws UnsupportedModelException {
		assert server != null;
		assert device != null;
		assert device.status != null;
		this.id = device.id;
		this.homeServer = server;
		this.name = (name == null || name.isEmpty()) ? device.id : name;

		deviceModel = DeviceModel.getModel(device);
		if (!deviceModel.getType().isRemoteControllable()) {
			throw new UnsupportedModelException(device.id);
		}
		powerMaxUnits = device.status.powerMax;
		status = NOT_CONNECTED;
		update(device);
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
	public synchronized UpdateResult update(Device device) {
		assert device != null;
		UpdateResult result = UpdateResult.NO_UPDATES;

		if (device.connected && status == NOT_CONNECTED) {
			setStatus(READY);
		} else if (!device.connected && status != ERROR) {
			setStatus(NOT_CONNECTED);
		}

		if (device.status != null) {
			final int newDemandPowerWatt = toPowerWatt(device.status.power);
			if (newDemandPowerWatt != demandPowerWatt) {
				if (demandPowerWatt == 0) {
					waterConsumptionStarted(newDemandPowerWatt);
				} else if (newDemandPowerWatt == 0) {
					waterConsumptionEnded();
				} else {
					waterConsumptionChanged(newDemandPowerWatt);
				}
				result = result.and(newDemandPowerWatt > 0 ? UpdateResult.URGENT_UPDATES : UpdateResult.MINOR_UPDATES);
			}

			actualDemandTemperature = device.status.setpoint;

			if (intakeWaterTemperature == UNDEFINED_TEMPERATURE || Math.abs(intakeWaterTemperature - device.status.tIn) > INTAKE_WATER_TEMP_CHANGE_IGNORE_DELTA) {
				intakeWaterTemperature = device.status.tIn;
				result = result.and(status.isConsuming() ? UpdateResult.URGENT_UPDATES : UpdateResult.MINOR_UPDATES);
			}
		}
		return result;
	}

	@Override
	public DeviceStatus getStatus() {
		return status;
	}

	protected void setStatus(DeviceStatus status) {
		this.status = status;
	}

	/**
	 * Invoked by the actual physical device.
	 * 
	 * @param demandPowerUnits
	 *            the power needed to satisfy the user demand (temperature, flow).
	 */
	void waterConsumptionStarted(int demandPowerWatt) {
		assert demandPowerWatt >= 0 && demandPowerWatt <= deviceModel.getPowerMaxWatt();
		this.demandPowerWatt = demandPowerWatt;
		consumptionStartTime = System.currentTimeMillis();
		setStatus(CONSUMPTION_STARTED); // needs approval by scheduler
	}

	void waterConsumptionChanged(final int newDemandPowerWatt) {
		demandPowerWatt = newDemandPowerWatt;
	}

	/**
	 * Invoked by the actual physical device.
	 */
	void waterConsumptionEnded() {
		demandPowerWatt = 0;
		consumptionStartTime = NO_CONSUMPTION;
		setStatus(DeviceStatus.READY);
	}

	@Override
	public synchronized void updateMaximumPowerConsumption(int approvedPowerWatt, ElmStatus elmStatus) {
		assert approvedPowerWatt >= 0 && approvedPowerWatt <= deviceModel.getPowerMaxWatt() || approvedPowerWatt == UNLIMITED_POWER;
		final int newApprovedPowerWatt = approvedPowerWatt == deviceModel.getPowerMaxWatt() ? UNLIMITED_POWER : approvedPowerWatt;
		if (internalApprovedPowerWatt != newApprovedPowerWatt || status == CONSUMPTION_STARTED) {
			setApprovedPowerWatt(newApprovedPowerWatt);
			if (status.isConsuming()) {
				if (newApprovedPowerWatt == 0) {
					setStatus(CONSUMPTION_DENIED);
				} else if (newApprovedPowerWatt == UNLIMITED_POWER || newApprovedPowerWatt >= this.demandPowerWatt) {
					setStatus(CONSUMPTION_APPROVED);
				} else {
					setStatus(CONSUMPTION_LIMITED);
				}
			}
		}
	}

	private void setApprovedPowerWatt(final int newApprovedPowerWatt) {
		internalApprovedPowerWatt = newApprovedPowerWatt;
		// scald protection
		if (internalApprovedPowerWatt == UNLIMITED_POWER) {
			scaldProtectionTemperature = UNDEFINED_TEMPERATURE;
			// restore user-defined demand temperature (ASYNCHRONOUS device update):
			getHomeServer().putDeviceUpdate(
					new ClearScaldProtection(this, userDemandTemperature == UNDEFINED_TEMPERATURE ? actualDemandTemperature : userDemandTemperature));
			userDemandTemperature = UNDEFINED_TEMPERATURE;

		} else { // limited power
			scaldProtectionTemperature = toTemperatureLimit(newApprovedPowerWatt);
			assert scaldProtectionTemperature >= deviceModel.getScaldProtectionTemperatureMin();
			if (userDemandTemperature == UNDEFINED_TEMPERATURE) {
				userDemandTemperature = actualDemandTemperature;
			}
			// ASYNCHRONOUS device update:
			getHomeServer().putDeviceUpdate(new SetScaldProtectionTemperature(this, scaldProtectionTemperature));
		}
	}

	/**
	 * @param approvedPowerWatt
	 * @return the temperature in [1/10°C] that requires the given amount of power
	 */
	private short toTemperatureLimit(final int approvedPowerWatt) {
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
	public short getActualDemandTemperature() {
		return actualDemandTemperature;
	}

	/** Used for testing. */
	public short getUserDemandTemperature() {
		return userDemandTemperature;
	}

	int toPowerWatt(short powerUnits) {
		assert powerMaxUnits != 0;
		return deviceModel.getPowerMaxWatt() * powerUnits / powerMaxUnits;
	}

	short toPowerUnits(int powerWatt) {
		assert powerMaxUnits != 0;
		return (short) (powerWatt * powerMaxUnits / deviceModel.getPowerMaxWatt());
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
