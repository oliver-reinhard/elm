package elm.sim.model;

public enum Status {
	OFF("Aus"), ON("Ein"), SATURATION("Sättigung"), OVERLOAD("Überlast"), ERROR("Fehler");
	
	private final String label;

	private Status(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
