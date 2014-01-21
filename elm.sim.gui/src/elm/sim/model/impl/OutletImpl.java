package elm.sim.model.impl;

import static elm.sim.model.Status.OFF;
import static elm.sim.model.Status.ON;
import static elm.sim.model.Status.OVERLOAD;
import static elm.sim.model.Status.SATURATION;

import java.util.logging.Logger;

import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.DemandEnablement;
import elm.sim.model.Flow;
import elm.sim.model.Outlet;
import elm.sim.model.Status;
import elm.sim.model.Temperature;

/**
 * Apart from the name, which is mandatory, all fields are optional.
 */
public class OutletImpl extends AbstractSimObject implements Outlet {

	private static final Logger LOG = Logger.getLogger(OutletImpl.class.getName());

	/** Outlet identification within group. */
	private final String name;

	/** Flow as requested by user. */
	private Flow demandFlow = Flow.NONE; // new outlets typically are not running when they're installed

	/** Temperature as requested by user. */
	private Temperature demandTemperature;

	/** How the user can change the demand parameters at the moment. */
	private DemandEnablement demandEnablement = DemandEnablement.OFF;

	/** The status of the outlet. */
	private Status status = OFF;

	/** The waiting time indication if status == {@link Status#OVERLOAD}. */
	private int waitingTimePercent = NO_WAITING_PERCENT;

	/** Mirror of the scheduler's status. */
	private Status schedulerStatus = OFF;

	/** Flow as granted by scheduler. */
	private Flow actualFlow = Flow.NONE; // new outlets typically are not running when they're installed

	public OutletImpl(String name, Temperature demandTemperature) {
		assert name != null && !name.isEmpty();
		this.name = name;
		this.demandTemperature = demandTemperature;
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
	public void setDemandFlow(Flow newValue) {
		assert newValue != null;
		assert getDemandEnablement() != DemandEnablement.OFF;
		Flow oldValue = demandFlow;
		if (oldValue != newValue) {
			demandFlow = newValue;
			fireModelChanged(Attribute.DEMAND_FLOW, oldValue, newValue);
			updateDerived();
		}
	}

	@Override
	public Flow getDemandFlow() {
		return demandFlow;
	}

	@Override
	public void setDemandTemperature(Temperature newValue) {
		assert newValue != null;
		assert getDemandEnablement() != DemandEnablement.OFF;
		Temperature oldValue = demandTemperature;
		if (oldValue != newValue) {
			demandTemperature = newValue;
			fireModelChanged(Attribute.DEMAND_TEMPERATURE, oldValue, newValue);
			updateDerived();
		}
	}

	@Override
	public Temperature getDemandTemperature() {
		return demandTemperature;
	}

	/**
	 * This method should not be called from outside this class.
	 * 
	 * @param newValue
	 *            cannot be {@code null}
	 */
	protected void setDemandEnabled(DemandEnablement newValue) {
		assert newValue != null;
		DemandEnablement oldValue = demandEnablement;
		if (oldValue != newValue || newValue == DemandEnablement.DOWN) { // on "down" the upper limit of demand flow is lowered (again)
			demandEnablement = newValue;
			fireModelChanged(Attribute.DEMAND_ENABLEMENT, oldValue, newValue);
		}
	}

	public DemandEnablement getDemandEnablement() {
		return demandEnablement;
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
	public Status getStatus() {
		return status;
	}

	@Override
	public void setSchedulerStatus(Status schedulerStatus) {
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
	 * <li>demandEnabled</li>
	 * </ul>
	 * on the basis of these values
	 * <ul>
	 * <li>schedulerStatus</li>
	 * <li>demandFlowFlow</li>
	 * <li>demandTemperature</li>
	 * </ul>
	 */
	protected void updateDerived() {
		// Are we in the middle of an actual flow?
		if (actualFlow.isOn()) {
			if (schedulerStatus.in(ON, SATURATION, OVERLOAD)) {
				if (demandFlow.isOn()) {
					setStatus(ON);
					// allow to increase or decrease the demand
					setDemandEnabled(DemandEnablement.UP_DOWN);
					LOG.info("A1 — change current flow, but don't turn it off");
				} else if (schedulerStatus == OVERLOAD) { // demand := off
					setStatus(schedulerStatus);
					setDemandEnabled(DemandEnablement.OFF);
					LOG.info("A2 - turn current flow off in Overload");
				} else {
					setStatus(schedulerStatus);
					// allow to increase or decrease the demand
					setDemandEnabled(DemandEnablement.UP_DOWN);
					LOG.info("A3 - turn current flow off");
				}
				setActualFlow(demandFlow);

			} else { // schedulerStatus.in(OFF, ERROR)
				setStatus(schedulerStatus);  // user can see why demand-flow increase is disabled
				// allow to decrease the demand:
				setDemandEnabled(DemandEnablement.DOWN);
				if (demandFlow.lessThan(actualFlow)) {
					setActualFlow(demandFlow);
					LOG.info("A4 - turn current flow down");
				} else {
					LOG.info("A5 - no change of current flow");
				}
			}

		} else if (schedulerStatus.in(ON, SATURATION)) {
			if (demandFlow.isOn()) {
				setStatus(ON);
				LOG.info("B1 - turn current flow on");
			} else {
				setStatus(schedulerStatus);
				LOG.info("B2 - enable demand-flow change up or down");
			}
			// allow to increase or decrease the demand
			setDemandEnabled(DemandEnablement.UP_DOWN);
			setActualFlow(demandFlow);

		} else { // schedulerStatus.in(OFF, OVERLOAD, ERROR)
			setStatus(schedulerStatus);
			setDemandEnabled(DemandEnablement.OFF);
			LOG.info("C - disable demand-flow changes");
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

	@Override
	public void setActualFlow(Flow newValue) {
		assert newValue != null;
		Flow oldValue = actualFlow;
		if (oldValue != newValue) {
			actualFlow = newValue;
			fireModelChanged(Attribute.ACTUAL_FLOW, oldValue, newValue);
		}
	}

	@Override
	public Flow getActualFlow() {
		return actualFlow;
	}
}
