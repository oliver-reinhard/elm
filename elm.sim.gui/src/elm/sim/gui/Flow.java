package elm.sim.gui;

public enum Flow {
	
	LOW(4, "wenig"), MEDIUM(8, "mittel"), HIGH(12, "viel");
	
	private final int litresPerMinute;
	private final String label;
	
	Flow(int litresPerMinute, String label) {
		this.litresPerMinute = litresPerMinute;
		this.label = label;
	}

	public int getLitresPerMinute() {
		return litresPerMinute;
	}

	public String getLabel() {
		return label;
	}
}
