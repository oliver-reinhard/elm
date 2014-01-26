package elm.sim.metamodel;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractSimObject implements SimObject {

	private List<SimModelListener> modelListeners;
	
	public void addModelListener(SimModelListener listener) {
		if (modelListeners == null) {
			modelListeners = new ArrayList<SimModelListener>();
			modelListeners.add(listener);
		} else if (!modelListeners.contains(listener)) {
			modelListeners.add(listener);
		}
	}
	
	public void removeModelListener(SimModelListener listener) {
		if (modelListeners != null) {
			modelListeners.remove(listener);
			if (modelListeners.isEmpty()) {
				modelListeners = null;
			}
		}
	}
	
	protected void fireModelChanged(SimAttribute attribute, Object oldValue, Object newValue) {
		if (modelListeners != null) {
			SimModelEvent event = new SimModelEvent(this, attribute, oldValue, newValue);
			for (SimModelListener listener : modelListeners) {
				listener.modelChanged(event);
			}
		}
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(getClass().getName());
		b.append("(\"");
		b.append(getLabel());
		b.append("\")");
		return b.toString();
	}
}