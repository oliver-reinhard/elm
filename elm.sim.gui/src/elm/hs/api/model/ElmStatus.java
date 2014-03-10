package elm.hs.api.model;

public enum ElmStatus {
	OFF("Aus"), ON("Ein"), SATURATION("Sättigung"), OVERLOAD("Überlast"), ERROR("Störung");

	final String label;

	private ElmStatus(String label) {
		this.label = label;
	}
	
	public String getLabel() {
		return label;
	}

	public boolean in(ElmStatus... other) {
		for (ElmStatus value : other) {
			if (this == value) return true;
		}
		return false;
	}
}
