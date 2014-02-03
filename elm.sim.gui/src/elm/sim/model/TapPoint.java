package elm.sim.model;

import elm.sim.metamodel.SimAttribute;

public interface TapPoint {

	/** A simple metamodel of the {@link TapPoint}. */
	public enum Attribute implements SimAttribute {

		NAME("Name"), REFERENCE_FLOW("Soll-Menge"), ACTUAL_FLOW("Ist-Menge"), REFERENCE_TEMPERATURE("Soll-Temperatur"), ACTUAL_TEMPERATURE("Ist-Temperatur"), SCALD_TEMPERATURE("Verbrühschutztemperatur"), STATUS("Status"), WAITING_TIME_PERCENT(
				"Wartezeit [%]"), ;

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

	void setReferenceFlow(Flow newValue);

	Flow getReferenceFlow();

	void setActualFlow(Flow newValue);

	Flow getActualFlow();

	void setReferenceTemperature(Temperature newValue);

	Temperature getReferenceTemperature();

	void setActualTemperature(Temperature newValue);

	Temperature getActualTemperature();

	Temperature getScaldTemperature();

	Status getStatus();

	void setSchedulerStatus(Status schedulerStatus);

	void setWaitingTimePercent(int newValue);

	int getWaitingTimePercent();

}