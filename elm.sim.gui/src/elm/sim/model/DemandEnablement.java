package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum DemandEnablement implements SimEnum {
	OFF("Aus"), DOWN("Ab"), UP_DOWN("Auf+Ab");
	
	private final String label;

	private DemandEnablement(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
