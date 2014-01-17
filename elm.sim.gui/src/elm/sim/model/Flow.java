package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum Flow implements SimEnum {
	
	NONE(0, "aus"), LOW(4, "wenig"), MEDIUM(8, "mittel"), HIGH(12, "viel");
	
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
	
	public boolean greaterThan(Flow other) {
		return this.getLitresPerMinute() > other.getLitresPerMinute();
	}
	
	public boolean lessThan(Flow other) {
		return this.getLitresPerMinute() < other.getLitresPerMinute();
	}
}
