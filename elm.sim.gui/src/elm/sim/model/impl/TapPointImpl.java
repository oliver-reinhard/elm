package elm.sim.model.impl;

import static elm.sim.model.SimStatus.OFF;
import static elm.sim.model.SimStatus.ON;
import static elm.sim.model.SimStatus.OVERLOAD;
import static elm.sim.model.SimStatus.SATURATION;
import elm.hs.api.model.ElmStatus;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.Flow;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.SimStatus;
import elm.sim.model.TapPoint;

/**
 * Apart from the name, which is mandatory, all fields are optional.
 */
public class TapPointImpl extends AbstractSimObject implements TapPoint {

	/** Joule per gram and Kelvin. */
	private static final double WATER_HEAT_CAPACITY = 4.192;

	/** Outlet identification within group. */
	private final String name;

	/** ID of physical device. */
	private final String deviceId;

	/** Is there a real physical device behind this tap point? */
	private final boolean simDevice;

	private final DeviceModel deviceModel;

	/** Flow as requested by user. */
	private Flow referenceFlow = Flow.NONE; // new outlets typically are not running when they're installed

	/** Flow as granted by scheduler. */
	private Flow actualFlow = Flow.NONE; // new outlets typically are not running when they're installed

	/** Temperature as requested by user. */
	private HotWaterTemperature referenceTemperature;

	/** Temperature as constrained by the scheduler. */
	private HotWaterTemperature scaldProtectionTemperature = HotWaterTemperature.TEMP_MAX_60; // = no limit

	/** Temperature as granted by scheduler. */
	private HotWaterTemperature actualTemperature = HotWaterTemperature.TEMP_MIN_19; // = cold water

	/** The status of the outlet. */
	private SimStatus status = OFF;

	/** The waiting time indication if status == {@link SimStatus#OVERLOAD}. */
	private int waitingTimePercent = 0;

	private IntakeWaterTemperature waterIntakeTemperature;

	/** Mirror of the scheduler's status. */
	private SimStatus schedulerStatus = null;

	/** Time the flow went from 0 to a value greater than 0. */
	private long consumptionStartTimeMillis = NO_CONSUMPTION;

	/**
	 * 
	 * @param name
	 *            user-readable device description, cannot be {@code null} or empty
	 * @param id
	 *            device ID, cannot be {@code null} or empty
	 * @param simDevice
	 *            is this device only a simulation (vs. a real, physical device)
	 * @param referenceTemperature
	 *            cannot be {@code null}
	 * @throws UnsupportedDeviceModelException
	 */
	public TapPointImpl(String name, String id, boolean simDevice, HotWaterTemperature referenceTemperature) throws UnsupportedDeviceModelException {
		assert name != null && !name.isEmpty();
		assert id != null && !id.isEmpty();
		this.name = name;
		deviceId = id;
		deviceModel = DeviceModel.getModel(id);
		if (!deviceModel.getType().isRemoteControllable()) {
			throw new UnsupportedDeviceModelException(id);
		}
		this.simDevice = simDevice;
		this.referenceTemperature = referenceTemperature;
	}

	/**
	 * @return never {@code null} or empty
	 */
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getLabel() {
		return name;
	}

	@Override
	public String getId() {
		return deviceId;
	}

	@Override
	public boolean isSimDevice() {
		return simDevice;
	}

	@Override
	public DeviceModel getDeviceModel() {
		return deviceModel;
	}

	@Override
	public SimAttribute[] getSimAttributes() {
		return Attribute.values();
	}

	@Override
	public synchronized void setReferenceFlow(Flow newValue) {
		assert newValue != null;
		Flow oldValue = referenceFlow;
		if (oldValue != newValue) {
			referenceFlow = newValue;
			fireModelChanged(Attribute.REFERENCE_FLOW, oldValue, newValue);
			updateDerived();
		}
	}

	@Override
	public synchronized Flow getReferenceFlow() {
		return referenceFlow;
	}

	private void setActualFlow(Flow newValue) {
		assert newValue != null;
		Flow oldValue = actualFlow;
		if (oldValue != newValue) {
			actualFlow = newValue;
			fireModelChanged(Attribute.ACTUAL_FLOW, oldValue, newValue);
			if (oldValue == Flow.NONE && newValue.greaterThan(Flow.NONE)) {
				setConsumptionStartTimeMillis(System.currentTimeMillis());
			} else if (newValue == Flow.NONE) {
				setConsumptionStartTimeMillis(NO_CONSUMPTION);
			}
		}
	}

	@Override
	public synchronized Flow getActualFlow() {
		return actualFlow;
	}

	private void setConsumptionStartTimeMillis(long newValue) {
		long oldValue = consumptionStartTimeMillis;
		if (oldValue != newValue) {
			consumptionStartTimeMillis = newValue;
			fireModelChanged(Attribute.CONSUMPTION_START_TIME, oldValue, newValue);
		}
	}

	@Override
	public long getConsumptionStartTimeMillis() {
		return consumptionStartTimeMillis;
	}

	@Override
	public synchronized void setReferenceTemperature(HotWaterTemperature newValue) {
		assert newValue != null;
		HotWaterTemperature oldValue = referenceTemperature;
		if (oldValue != newValue) {
			referenceTemperature = newValue;
			fireModelChanged(Attribute.REFERENCE_TEMPERATURE, oldValue, newValue);
			updateDerived();
		}
	}

	@Override
	public synchronized HotWaterTemperature getReferenceTemperature() {
		return referenceTemperature;
	}

	private void setActualTemperature(HotWaterTemperature newValue) {
		assert newValue != null;
		assert newValue.lessOrEqualThan(getScaldProtectionTemperature()) : "actual temperature exceeds scald temperature";
		final int intakeTempCelcius = getIntakeWaterTemperature() != null ? getIntakeWaterTemperature().getDegreesCelsius() : newValue.getDegreesCelsius();
		HotWaterTemperature realNewValue = HotWaterTemperature.fromInt(Math.max(newValue.getDegreesCelsius(), intakeTempCelcius));
		HotWaterTemperature oldValue = actualTemperature;
		if (oldValue != realNewValue) {
			actualTemperature = realNewValue;
			fireModelChanged(Attribute.ACTUAL_TEMPERATURE, oldValue, newValue);
		}
	}

	@Override
	public synchronized HotWaterTemperature getActualTemperature() {
		return actualTemperature;
	}

	@Override
	public synchronized void setScaldProtectionTemperature(HotWaterTemperature newValue) {
		setInternalScaldProtectionTemperature(newValue, true);
	}

	/**
	 * @param newValue
	 *            cannot be {@code null}
	 * @param updateDerived
	 *            update derived values on true status change
	 */
	private void setInternalScaldProtectionTemperature(HotWaterTemperature newValue, boolean updateDerived) {
		assert newValue != null;
		HotWaterTemperature oldValue = scaldProtectionTemperature;
		if (oldValue != newValue) {
			scaldProtectionTemperature = newValue;
			fireModelChanged(Attribute.SCALD_PROTECTION_TEMPERATURE, oldValue, newValue);
			if (updateDerived) {
				updateDerived();
			}
		}
	}

	@Override
	public synchronized HotWaterTemperature getScaldProtectionTemperature() {
		return scaldProtectionTemperature;
	}

	/**
	 * @param newValue
	 *            cannot be {@code null}
	 * @param updateDerived
	 *            update derived values on true status change
	 */
	private void setInternalStatus(SimStatus newValue, boolean updateDerived) {
		assert newValue != null;
		SimStatus oldValue = status;
		if (oldValue != newValue) {
			status = newValue;
			fireModelChanged(Attribute.STATUS, oldValue, newValue);
			if (updateDerived) {
				updateDerived();
			}
		}
	}

	@Override
	public synchronized SimStatus getStatus() {
		return status;
	}

	@Override
	public void setStatus(ElmStatus deviceStatus) {
		if (schedulerStatus != null) {
			throw new IllegalStateException("Cannot use setStatus() after setSchedulerStatus() has been invoked. Consistently use one XOR the other.");
		}
		setInternalStatus(SimStatus.fromElmStatus(deviceStatus), true);
	}

	@Override
	public synchronized void setSchedulerStatus(SimStatus schedulerStatus) {
		assert schedulerStatus != null;
		// remember the scheduler status
		this.schedulerStatus = schedulerStatus;
		updateDerived();
	}

	protected void updateDerived() {
		// currently, the actual flow is unconstrained and always follows the reference flow
		setActualFlow(referenceFlow);

		if (schedulerStatus == null) {
			if (status == OFF) {
				setActualTemperature(HotWaterTemperature.TEMP_MIN_19);
			} else {
				setActualTemperature(referenceTemperature);
			}

		} else {
			// schedulerStatus != null
			//
			// Are we in the middle of an actual flow?
			if (actualFlow.isOn()) {
				// Yes => interfere as little as possible with the user's reference temperature;
				if (schedulerStatus.in(ON, SATURATION)) {
					setInternalStatus(ON, false);
					setInternalScaldProtectionTemperature(HotWaterTemperature.TEMP_MAX_60, false);
					// allow to increase or decrease the reference temperature:
					setActualTemperature(referenceTemperature);

				} else if (schedulerStatus == OVERLOAD && scaldProtectionTemperature != HotWaterTemperature.TEMP_MIN_19) {
					setInternalStatus(ON, false);
					setInternalScaldProtectionTemperature(HotWaterTemperature.TEMP_MAX_60, false);
					// allow to increase or decrease the reference temperature:
					setActualTemperature(referenceTemperature);

				} else { // schedulerStatus.in(OFF, ERROR)
					setInternalStatus(schedulerStatus, false); // user can see why reference-flow increase is disabled
					setInternalScaldProtectionTemperature(actualTemperature, false);
					// allow only to decrease the reference with respect to current actual temp.:
					setActualTemperature(HotWaterTemperature.min(actualTemperature, referenceTemperature));
				}

			} else if (schedulerStatus.in(ON, SATURATION)) {
				// No, there is no actual flow:
				setInternalStatus(schedulerStatus, false);
				setInternalScaldProtectionTemperature(HotWaterTemperature.TEMP_MAX_60, false);
				// allow to increase or decrease the reference
				setActualTemperature(referenceTemperature);

			} else { // schedulerStatus.in(OFF, OVERLOAD, ERROR)
				setInternalStatus(schedulerStatus, false);
				setInternalScaldProtectionTemperature(HotWaterTemperature.TEMP_MIN_19, false);
				setActualTemperature(HotWaterTemperature.TEMP_MIN_19);
			}
		}
	}

	@Override
	public void setWaitingTimeMillis(int newValue) {
		assert newValue >= 0;
		// ignore values when not in OVERLOAD
		if (status == SimStatus.OVERLOAD) {
			int oldValue = waitingTimePercent;
			if (oldValue != newValue) {
				waitingTimePercent = newValue;
				fireModelChanged(Attribute.WAITING_TIME_MILLIS, oldValue, newValue);
			}
		}
	}

	@Override
	public int getWaitingTimeMillis() {
		return waitingTimePercent;
	}

	@Override
	public void setIntakeWaterTemperature(IntakeWaterTemperature newValue) {
		assert newValue != null;
		IntakeWaterTemperature oldValue = waterIntakeTemperature;
		if (oldValue != newValue) {
			waterIntakeTemperature = newValue;
			fireModelChanged(Attribute.INTAKE_WATER_TEMPERATURE, oldValue, newValue);
			updateDerived();
		}
	}

	@Override
	public IntakeWaterTemperature getIntakeWaterTemperature() {
		return waterIntakeTemperature;
	}

	@Override
	public int getPowerWatt() {
		if (deviceModel == null) {
			throw new IllegalStateException("Must set a device ID before calling this method.");
		}
		if (internalIsHeaterOn()) {
			int powerWatt = (int) (WATER_HEAT_CAPACITY * (getActualTemperature().getDegreesCelsius() - getIntakeWaterTemperature().getDegreesCelsius())
					* getActualFlow().getMillilitresPerMinute() / 60);
			return Math.min(powerWatt, deviceModel.getPowerMaxWatt());
		} else {
			return 0;
		}
	}

	@Override
	public short getPowerUnits() {
		return toPowerUnits(getPowerWatt());
	}

	/**
	 * @return {@code true} if heater actually draws power; this is different from {@link #getFlags()}
	 */
	private boolean internalIsHeaterOn() {
		return getActualFlow().isOn() && getActualTemperature().getDegreesCelsius() > (deviceModel.getTemperatureOff() / 10)
				&& getActualTemperature().getDegreesCelsius() > getIntakeWaterTemperature().getDegreesCelsius();
	}

	@Override
	public short getFlags() {
		if (internalIsHeaterOn()
				|| getActualFlow().isOn()
				// also return true when the heater is off due to the scald-protection temperature being at the temperature_OFF:
				&& (getActualTemperature().getDegreesCelsius() == getScaldProtectionTemperature().getDegreesCelsius() && getScaldProtectionTemperature()
						.getDegreesCelsius() == deviceModel.getTemperatureOff() / 10)) {
			return 0;
		}
		return 1;
	}

	int toPowerWatt(short powerUnits) {
		return deviceModel.getPowerMaxWatt() * powerUnits / deviceModel.getPowerMaxUnits();
	}

	short toPowerUnits(int powerWatt) {
		return (short) (powerWatt * deviceModel.getPowerMaxUnits() / deviceModel.getPowerMaxWatt());
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(getClass().getSimpleName());
		b.append("(\"");
		b.append(getLabel());
		b.append("\", ");
		b.append(getId());
		b.append(")");
		return b.toString();
	}
}
