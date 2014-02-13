package elm.sim.model;

import elm.sim.metamodel.SimEnum;
import elm.ui.api.ElmStatus;

public enum SimStatus implements SimEnum {
	OFF("Aus"), ON("Ein"), SATURATION("Sättigung"), OVERLOAD("Überlast"), ERROR("Fehler");

	private final String label;

	private SimStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public boolean in(SimStatus... other) {
		for (SimStatus value : other) {
			if (this == value) return true;
		}
		return false;
	}

	public static SimStatus fromElmStatus(ElmStatus status) {
		assert status != null;
		switch (status) {
		case ERROR:
			return ERROR;
		case OFF:
			return OFF;
		case ON:
			return ON;
		case OVERLOAD:
			return OVERLOAD;
		case SATURATION:
			return SATURATION;
		default:
			throw new IllegalArgumentException(status.toString());
		}
	}
}
