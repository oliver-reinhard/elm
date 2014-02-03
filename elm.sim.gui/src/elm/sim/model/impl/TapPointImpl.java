package elm.sim.model.impl;

import static elm.sim.model.Status.OFF;
import static elm.sim.model.Status.ON;
import static elm.sim.model.Status.OVERLOAD;
import static elm.sim.model.Status.SATURATION;

import java.util.logging.Level;
import java.util.logging.Logger;

import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.Flow;
import elm.sim.model.TapPoint;
import elm.sim.model.Status;
import elm.sim.model.Temperature;

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
	private Temperature scaldTemperature = Temperature.TEMP_MAX; // = no limit

	/** Temperature as granted by scheduler. */
	private Temperature actualTemperature = Temperature.TEMP_MIN; // = cold water

	/** The status of the outlet. */
	private Status status = OFF;

	/** The waiting time indication if status == {@link Status#OVERLOAD}. */
	private int waitingTimePercent = NO_WAITING_PERCENT;

	/** Mirror of the scheduler's status. */
	private Status schedulerStatus = OFF;

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

	@Override
	public synchronized void setActualTemperature(Temperature newValue) {
		assert newValue != null;
		if (newValue.lessOrEqualThan(getScaldTemperature())) {
			Temperature oldValue = actualTemperature;
			if (oldValue != newValue) {
				actualTemperature = newValue;
				fireModelChanged(Attribute.ACTUAL_TEMPERATURE, oldValue, newValue);
				updateDerived();
			}
		} else {
			LOG.warning("actual temperature cannot exceed scald temperature");
		}
	}

	@Override
	public synchronized Temperature getActualTemperature() {
		return actualTemperature;
	}

	private void setScaldTemperature(Temperature newValue) {
		assert newValue != null;
		Temperature oldValue = scaldTemperature;
		if (oldValue != newValue) {
			scaldTemperature = newValue;
			fireModelChanged(Attribute.SCALD_TEMPERATURE, oldValue, newValue);
		}
	}

	@Override
	public synchronized Temperature getScaldTemperature() {
		return scaldTemperature;
	}

	/**
	 * Usually status changes are set via {@link #setSchedulerStatus(Status)}.
	 * 
	 * @param newValue
	 *            cannot be {@code null}
	 */
	protected void setStatus(Status newValue) {
		assert newValue != null;
		Status oldValue = status;
		if (oldValue != newValue) {
			status = newValue;
			fireModelChanged(Attribute.STATUS, oldValue, newValue);
		}
	}

	@Override
	public synchronized Status getStatus() {
		return status;
	}

	@Override
	public synchronized void setSchedulerStatus(Status schedulerStatus) {
		assert schedulerStatus != null;
		// remember the scheduler status
		this.schedulerStatus = schedulerStatus;
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
				setStatus(ON);
				// allow to increase or decrease the reference temperature:
				LOG.info("A1 — user can freely change temperature");
				setScaldTemperature(Temperature.TEMP_MAX);
				setActualTemperature(referenceTemperature);

			} else if (schedulerStatus == OVERLOAD && scaldTemperature != Temperature.TEMP_MIN) {
				setStatus(ON);
				// allow to increase or decrease the reference temperature:
				LOG.info("A2 — user can freely change temperature");
				setActualTemperature(referenceTemperature);

			} else { // schedulerStatus.in(OFF, ERROR)
				setStatus(schedulerStatus); // user can see why reference-flow increase is disabled
				// allow only to decrease the reference:
				LOG.info("A3 — user can only reduce temperature");
				setScaldTemperature(actualTemperature);
				setActualTemperature(Temperature.min(referenceTemperature, scaldTemperature));
			}

		} else if (schedulerStatus.in(ON, SATURATION)) {
			// No, there is no actual flow:
			setStatus(schedulerStatus);
			LOG.info("B - enable reference temperature change up or down");
			// allow to increase or decrease the reference
			setScaldTemperature(Temperature.TEMP_MAX);
			setActualTemperature(referenceTemperature);

		} else { // schedulerStatus.in(OFF, OVERLOAD, ERROR)
			setStatus(schedulerStatus);
			LOG.info("C - cold water only");
			setScaldTemperature(Temperature.TEMP_MIN);
			setActualTemperature(Temperature.TEMP_MIN);
		}
	}

	@Override
	public void setWaitingTimePercent(int newValue) {
		assert newValue >= NO_WAITING_PERCENT && newValue <= MAX_WAITING_PERCENT;
		// ignore values when not in OVERLOAD
		if (status == Status.OVERLOAD) {
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
