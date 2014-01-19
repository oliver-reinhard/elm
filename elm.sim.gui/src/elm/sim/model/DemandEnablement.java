package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum DemandEnablement implements SimEnum {
	/** Demand values cannot be modified. */
	OFF("Aus"),
	/** Demand values can be decreased from their current value towards a safer value but not be increased. */
	DOWN("Ab"),
	/** Demand values can be increased or decreased freely. */ 
	UP_DOWN("Auf+Ab");
	
	private final String label;

	private DemandEnablement(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
