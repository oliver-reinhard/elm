package elm.sim.model.impl;

import static elm.sim.model.SimStatus.OFF;
import static elm.sim.model.SimStatus.ON;
import static elm.sim.model.SimStatus.OVERLOAD;
import static elm.sim.model.SimStatus.SATURATION;

import java.util.logging.Level;
import java.util.logging.Logger;

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

	private static final Logger LOG = Logger.getLogger(TapPointImpl.class.getName());

	{
		LOG.setLevel(Level.WARNING);
	}

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
	private SimStatus schedulerStatus = OFF;

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

	@Override
	public synchronized void setActualFlow(Flow newValue) {
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
		if (newValue.lessOrEqualThan(getScaldProtectionTemperature())) {
			Temperature oldValue = actualTemperature;
			if (oldValue != newValue) {
				actualTemperature = newValue;
				fireModelChanged(Attribute.ACTUAL_TEMPERATURE, oldValue, newValue);
			}
		} else {
			LOG.warning("actual temperature cannot exceed scald temperature");
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
		setInternalStatus(SimStatus.fromElmStatus(deviceStatus), true);
	}

	@Override
	public synchronized void setSchedulerStatus(SimStatus schedulerStatus) {
		assert schedulerStatus != null;
		// remember the scheduler status
		this.schedulerStatus = schedulerStatus;

		// infer scald-protection temperature
		if (actualFlow.isOn() || schedulerStatus.in(ON, SATURATION)) {
			setInternalScaldProtectionTemperature(Temperature.TEMP_MAX, false);
		} else {
			setInternalScaldProtectionTemperature(Temperature.TEMP_MIN, false);
		}
		updateDerived();
	}

	/**
	 * Updates the following derived values
	 * <ul>
	 * <li>status</li>
	 * <li>actualFlow</li>
	 * <li>referenceEnabled</li>
	 * </ul>
	 * on the basis of these values
	 * <ul>
	 * <li>schedulerStatus</li>
	 * <li>referenceFlow</li>
	 * <li>referenceTemperature</li>
	 * <li>scaldTemperature</li>
	 * </ul>
	 */
	protected void updateDerived() {
		// currently, the actual flow is unconstrained and always follows the reference flow
		setActualFlow(referenceFlow);

		// Are we in the middle of an actual flow?
		if (actualFlow.isOn()) {
			// Yes => interfere as little as possible with the user's reference temperature;
			if (schedulerStatus.in(ON, SATURATION)) {
				setInternalStatus(ON, false);
				// allow to increase or decrease the reference temperature:
				LOG.info("A1 — user can freely change temperature");
				setActualTemperature(referenceTemperature);

			} else if (schedulerStatus == OVERLOAD && scaldProtectionTemperature != Temperature.TEMP_MIN) {
				setInternalStatus(ON, false);
				// allow to increase or decrease the reference temperature:
				LOG.info("A2 — user can freely change temperature");
				setActualTemperature(referenceTemperature);

			} else { // schedulerStatus.in(OFF, ERROR)
				setInternalStatus(schedulerStatus, false); // user can see why reference-flow increase is disabled
				// allow only to decrease the reference with respect to current actual temp.:
				LOG.info("A3 — user can only reduce temperature");
				setActualTemperature(Temperature.min(actualTemperature, referenceTemperature, scaldProtectionTemperature));
			}

		} else if (schedulerStatus.in(ON, SATURATION)) {
			// No, there is no actual flow:
			setInternalStatus(schedulerStatus, false);
			LOG.info("B - enable reference temperature change up or down");
			// allow to increase or decrease the reference
			setActualTemperature(referenceTemperature);

		} else { // schedulerStatus.in(OFF, OVERLOAD, ERROR)
			setInternalStatus(schedulerStatus, false);
			LOG.info("C - cold water only");
			setActualTemperature(Temperature.TEMP_MIN);
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
