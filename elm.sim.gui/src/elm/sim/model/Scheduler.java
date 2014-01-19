package elm.sim.model;

import elm.sim.metamodel.SimAttribute;
import elm.sim.model.impl.SchedulerImpl;

public interface Scheduler {

	/** A simple metamodel of the {@link SchedulerImpl}. */
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

	void setStatus(Status newValue);

	Status getStatus();

	/**
	 * The waiting time is only set if {@link #getStatus()} returns {@link Status#OVERLOAD}.
	 * 
	 * @return a value between {@code 0} and {@code 100}
	 */
	int getWaitingTimePercent();
}