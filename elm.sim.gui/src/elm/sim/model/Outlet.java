package elm.sim.model;

import static elm.sim.model.Status.OFF;
import static elm.sim.model.Status.ON;
import static elm.sim.model.Status.OVERLOAD;
import static elm.sim.model.Status.SATURATION;

import java.util.logging.Logger;

import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;

/**
 * Apart from the name, which is mandatory, all fields are optional.
 */
public class Outlet extends AbstractSimObject {

	/** A simple metamodel of the {@link Outlet}. */
	public enum Attribute implements SimAttribute {

		NAME("Name"), DEMAND_FLOW("Soll-Menge"), DEMAND_TEMPERATURE("Soll-Temperatur"), DEMAND_ENABLEMENT("Soll möglich"), STATUS("Status"), WAITING_TIME_PERCENT(
				"Wartezeit [%]"), ACTUAL_FLOW("Ist-Menge");

		private final String label;

		private Attribute(String label) {
			this.label = label;
		}

		public String id() {
			return "OUTLET__" + name();
		}

		@Override
		public String getLabel() {
			return label;
		}
	}

	private static final Logger LOG = Logger.getLogger(Outlet.class.getName());

	public static final int NO_WAITING_PERCENT = 0;
	public static final int MAX_WAITING_PERCENT = 100;

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

	public Outlet(String name, Temperature demandTemperature) {
		assert name != null && !name.isEmpty();
		this.name = name;
		this.demandTemperature = demandTemperature;
	}

	/**
	 * @return never {@code null} or empty
	 */
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

	public Flow getDemandFlow() {
		return demandFlow;
	}

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

	public Temperature getDemandTemperature() {
		return demandTemperature;
	}

	protected void setDemandEnabled(DemandEnablement newValue) {
		assert newValue != null;
		DemandEnablement oldValue = demandEnablement;
		if (oldValue != newValue || newValue == DemandEnablement.DOWN) {  // on "down" the upper limit of demand flow is lowered (again)
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

	public Status getStatus() {
		return status;
	}

	public void setSchedulerStatus(Status schedulerStatus) {
		assert schedulerStatus != null;
		// remember the scheduler status
		this.schedulerStatus = schedulerStatus;
		updateDerived();
	}

	/**
	 * Updates the following derived values:
	 * <ul>
	 * <li>status</li>
	 * <li>actualFlow</li>
	 * <li>demandEnabled</li>
	 * </ul>
	 * on the basis of these values:
	 * <ul>
	 * <li>schedulerStatus</li>
	 * <li>demandFlowFlow</li>
	 * <li>demandTemperature</li>
	 * </ul>
	 */
	protected void updateDerived() {
		// Are we in the middle of an actual flow?
		if (actualFlow.greaterThan(Flow.NONE)) {
			if (schedulerStatus.in(ON, SATURATION, OVERLOAD)) {
				if (demandFlow.greaterThan(Flow.NONE)) {
					setStatus(ON);
					// allow to increase or decrease the demand
					setDemandEnabled(DemandEnablement.UP_DOWN);
					LOG.info("A1 — change current flow, but don't turn it off");
				} else if (schedulerStatus == OVERLOAD) { // demand := off
					setStatus(schedulerStatus);
					setDemandEnabled(DemandEnablement.OFF);
					LOG.info("A2 - turn current flow off in Overload");
				} else {
					// allow to increase or decrease the demand
					setDemandEnabled(DemandEnablement.UP_DOWN);
					LOG.info("A3 - turn current flow off");
				}
				setActualFlow(demandFlow);

			} else { // schedulerStatus.in(OFF, ERROR)
				setStatus(schedulerStatus);
				// allow to decrease the demand
				setDemandEnabled(DemandEnablement.DOWN); // enable reduction
				if (demandFlow.lessThan(actualFlow)) {
					setActualFlow(demandFlow);
					LOG.info("A4 - turn current flow down");
				} else {
					LOG.info("A5 - no change of current flow");
				}
			}

		} else if (schedulerStatus.in(ON, SATURATION)) {
			if (demandFlow.greaterThan(Flow.NONE)) {
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

	public void setWaitingTimePercent(int newValue) {
		assert newValue >= NO_WAITING_PERCENT && newValue <= MAX_WAITING_PERCENT;
		int oldValue = waitingTimePercent;
		if (oldValue != newValue) {
			waitingTimePercent = newValue;
			fireModelChanged(Attribute.WAITING_TIME_PERCENT, oldValue, newValue);
		}
	}

	public int getWaitingTimePercent() {
		return waitingTimePercent;
	}

	public void setActualFlow(Flow newValue) {
		assert newValue != null;
		Flow oldValue = actualFlow;
		if (oldValue != newValue) {
			actualFlow = newValue;
			fireModelChanged(Attribute.ACTUAL_FLOW, oldValue, newValue);
		}
	}

	public Flow getActualFlow() {
		return actualFlow;
	}
}
