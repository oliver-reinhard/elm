package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum Flow implements SimEnum {
	
	NONE(0, "Aus"),
	MIN(4500, "Min."), // this is the technical minimum the outlet can actively limit the flow to.
	MEDIUM(8000, "Mittel"), MAX(12000, "Max.");
	
	private final int millilitresPerMinute;
	private final String label;
	
	Flow(int millilitresPerMinute, String label) {
		this.millilitresPerMinute = millilitresPerMinute;
		this.label = label;
	}

	public int getMillilitresPerMinute() {
		return millilitresPerMinute;
	}

	public String getLabel() {
		return label;
	}
	
	public boolean isOn() {
		return this != NONE;
	}
	
	public boolean greaterThan(Flow other) {
		return this.getMillilitresPerMinute() > other.getMillilitresPerMinute();
	}
	
	public boolean lessThan(Flow other) {
		return this.getMillilitresPerMinute() < other.getMillilitresPerMinute();
	}
}
