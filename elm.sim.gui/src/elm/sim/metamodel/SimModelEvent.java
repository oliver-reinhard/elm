package elm.sim.metamodel;

public class SimModelEvent {

	private final SimLabeled source;
	private final SimAttribute attribute;
	private final Object oldValue;
	private final Object newValue;
	
	public SimModelEvent(SimLabeled source, SimAttribute attribute, Object oldValue, Object newValue) {
		this.source = source;
		this.attribute = attribute;
		this.oldValue = oldValue;
		this.newValue = newValue;
	}

	public SimLabeled getSource() {
		return source;
	}

	public SimAttribute getAttribute() {
		return attribute;
	}

	public Object getOldValue() {
		return oldValue;
	}

	public Object getNewValue() {
		return newValue;
	}	
}
