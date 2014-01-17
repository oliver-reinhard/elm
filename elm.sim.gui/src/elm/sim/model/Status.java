package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum Status implements SimEnum {
	OFF("Aus"), ON("Ein"), SATURATION("Sättigung"), OVERLOAD("Überlast"), ERROR("Fehler");
	
	private final String label;

	private Status(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
	
	public boolean in(Status... other) {
		for (Status value: other) {
			if (this == value) return true;
		}
		return false;
	}
}
