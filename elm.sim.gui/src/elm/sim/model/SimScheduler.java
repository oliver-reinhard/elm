package elm.sim.model;

import elm.sim.metamodel.SimAttribute;
import elm.sim.metamodel.SimChangeNotifier;

public interface SimScheduler extends SimChangeNotifier {

	/** A simple metamodel of the {@link SimScheduler}. */
	public enum Attribute implements SimAttribute {
		STATUS("Status"), WAITING_TIME_SECONDS("Wartezeit [s]");

		private final String label;

		private Attribute(String label) {
			this.label = label;
		}

		public String id() {
			return "SCHEDULER__" + name();
		}

		@Override
		public String getLabel() {
			return label;
		}
	}

	public static final int NO_WAITING_TIME = 0;

	String getLabel();

	SimAttribute[] getSimAttributes();

	void setStatus(SimStatus newValue);

	SimStatus getStatus();

	/**
	 * The waiting time is only set if {@link #getStatus()} returns {@link SimStatus#OVERLOAD}.
	 * 
	 * @return a value between {@code 0} and {@code 100}
	 */
	int getWaitingTimePercent();
}