package elm.sim.model.impl;

import static elm.sim.model.SimStatus.OFF;
import static elm.sim.model.SimStatus.ON;
import static elm.sim.model.SimStatus.OVERLOAD;
import static elm.sim.model.SimStatus.SATURATION;
import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.Flow;
import elm.sim.model.SimStatus;
import elm.sim.model.TapPoint;
import elm.sim.model.Temperature;
import elm.ui.api.ElmStatus;

/**
 * Apart from the name, which is mandatory, all fields are optional.
 */
public class TapPointImpl extends AbstractSimObject implements TapPoint {

	/** Outlet identification within group. */
	private final String name;

	/** Flow as requested by user. */
	private Flow referenceFlow = Flow.NONE; // new outlets typically are not running when they're installed

	/** Flow as granted by scheduler. */
	private Flow actualFlow = Flow.NONE; // new outlets typically are not running when they're installed

	/** Temperature as requested by user. */
	private Temperature referenceTemperature;

	/** Temperature as constrained by the scheduler. */
	private Temperature scaldProtectionTemperature = Temperature.TEMP_MAX; // = no limit

	/** Temperature as granted by scheduler. */
	private Temperature actualTemperature = Temperature.TEMP_MIN; // = cold water

	/** The status of the outlet. */
	private SimStatus status = OFF;

	/** The waiting time indication if status == {@link SimStatus#OVERLOAD}. */
	private int waitingTimePercent = NO_WAITING_PERCENT;

	/** Mirror of the scheduler's status. */
	private SimStatus schedulerStatus = null;

	public TapPointImpl(String name, Temperature referenceTemperature) {
		assert name != null && !name.isEmpty();
		this.name = name;
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
		}
	}

	@Override
	public synchronized Flow getActualFlow() {
		return actualFlow;
	}

	@Override
	public synchronized void setReferenceTemperature(Temperature newValue) {
		assert newValue != null;
		Temperature oldValue = referenceTemperature;
		if (oldValue != newValue) {
			referenceTemperature = newValue;
			fireModelChanged(Attribute.REFERENCE_TEMPERATURE, oldValue, newValue);
			updateDerived();
		}
	}

	@Override
	public synchronized Temperature getReferenceTemperature() {
		return referenceTemperature;
	}

	private void setActualTemperature(Temperature newValue) {
		assert newValue != null;
		assert newValue.lessOrEqualThan(getScaldProtectionTemperature()) : "actual temperature exceeds scald temperature";
		Temperature oldValue = actualTemperature;
		if (oldValue != newValue) {
			actualTemperature = newValue;
			fireModelChanged(Attribute.ACTUAL_TEMPERATURE, oldValue, newValue);
		}
	}

	@Override
	public synchronized Temperature getActualTemperature() {
		return actualTemperature;
	}

	@Override
	public synchronized void setScaldProtectionTemperature(Temperature newValue) {
		setInternalScaldProtectionTemperature(newValue, true);
	}

	/**
	 * @param newValue
	 *            cannot be {@code null}
	 * @param updateDerived
	 *            update derived values on true status change
	 */
	private void setInternalScaldProtectionTemperature(Temperature newValue, boolean updateDerived) {
		assert newValue != null;
		Temperature oldValue = scaldProtectionTemperature;
		if (oldValue != newValue) {
			scaldProtectionTemperature = newValue;
			fireModelChanged(Attribute.SCALD_TEMPERATURE, oldValue, newValue);
			if (updateDerived) {
				updateDerived();
			}
		}
	}

	@Override
	public synchronized Temperature getScaldProtectionTemperature() {
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
				setActualTemperature(Temperature.TEMP_MIN);
			}else {
				setActualTemperature(referenceTemperature);
			}
			
		} else {
			// Are we in the middle of an actual flow?
			if (actualFlow.isOn()) {
				// Yes => interfere as little as possible with the user's reference temperature;
				if (schedulerStatus.in(ON, SATURATION)) {
					setInternalStatus(ON, false);
					setInternalScaldProtectionTemperature(Temperature.TEMP_MAX, false);
					// allow to increase or decrease the reference temperature:
					setActualTemperature(referenceTemperature);

				} else if (schedulerStatus == OVERLOAD && scaldProtectionTemperature != Temperature.TEMP_MIN) {
					setInternalStatus(ON, false);
					setInternalScaldProtectionTemperature(Temperature.TEMP_MAX, false);
					// allow to increase or decrease the reference temperature:
					setActualTemperature(referenceTemperature);

				} else { // schedulerStatus.in(OFF, ERROR)
					setInternalStatus(schedulerStatus, false); // user can see why reference-flow increase is disabled
					setInternalScaldProtectionTemperature(actualTemperature, false);
					// allow only to decrease the reference with respect to current actual temp.:
					setActualTemperature(Temperature.min(actualTemperature, referenceTemperature));
				}

			} else if (schedulerStatus.in(ON, SATURATION)) {
				// No, there is no actual flow:
				setInternalStatus(schedulerStatus, false);
				setInternalScaldProtectionTemperature(Temperature.TEMP_MAX, false);
				// allow to increase or decrease the reference
				setActualTemperature(referenceTemperature);

			} else { // schedulerStatus.in(OFF, OVERLOAD, ERROR)
				setInternalStatus(schedulerStatus, false);
				setInternalScaldProtectionTemperature(Temperature.TEMP_MIN, false);
				setActualTemperature(Temperature.TEMP_MIN);
			}
		}
	}

	@Override
	public void setWaitingTimePercent(int newValue) {
		assert newValue >= NO_WAITING_PERCENT && newValue <= MAX_WAITING_PERCENT;
		// ignore values when not in OVERLOAD
		if (status == SimStatus.OVERLOAD) {
			int oldValue = waitingTimePercent;
			if (oldValue != newValue) {
				waitingTimePercent = newValue;
				fireModelChanged(Attribute.WAITING_TIME_PERCENT, oldValue, newValue);
			}
		}
	}

	@Override
	public int getWaitingTimePercent() {
		return waitingTimePercent;
	}
}
