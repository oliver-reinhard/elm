package elm.sim.model;

import elm.sim.metamodel.SimAttribute;
import elm.sim.model.impl.OutletImpl;

public interface Outlet {

	/** A simple metamodel of the {@link OutletImpl}. */
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

	public static final int NO_WAITING_PERCENT = 0;
	public static final int MAX_WAITING_PERCENT = 100;

	/**
	 * @return never {@code null} or empty
	 */
	String getName();

	String getLabel();

	SimAttribute[] getSimAttributes();

	void setDemandFlow(Flow newValue);

	Flow getDemandFlow();

	void setDemandTemperature(Temperature newValue);

	Temperature getDemandTemperature();

	Status getStatus();

	void setSchedulerStatus(Status schedulerStatus);

	void setWaitingTimePercent(int newValue);

	int getWaitingTimePercent();

	void setActualFlow(Flow newValue);

	Flow getActualFlow();

}