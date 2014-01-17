package elm.sim.model;

import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;

public class Scheduler extends AbstractSimObject {

	/** A simple metamodel of the {@link Outlet}. */
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

	private Status status = Status.OFF;

	/** Forecasted waiting time in seconds; {@code 0} means currently no waiting time. */
	private int waitingTimeSeconds = NO_WAITING_TIME;

	@Override
	public String getLabel() {
		return "Scheduler";
	}

	@Override
	public SimAttribute[] getSimAttributes() {
		return Attribute.values();
	}

	public void setStatus(Status newValue) {
		assert newValue != null;
		Status oldValue = status;
		if (oldValue != newValue) {
			status = newValue;
			fireModelChanged(Attribute.STATUS, oldValue, newValue);
			if (newValue == Status.OVERLOAD) {
				simulateWaitTime();
			}
		}
	}

	private void simulateWaitTime() {
		Thread thread = new Thread("Wait Time Simulator") {
			@Override
			public synchronized void run() {
				for(int t = 6; t>=0; t--) {
					setWaitingTimeSeconds(t);
					try {
						wait(1000);
					} catch (InterruptedException e) {
						break;
					}
				}
				setStatus(Status.SATURATION);
			}
		};
		thread.start();
	}

	public Status getStatus() {
		return status;
	}

	/**
	 * Should not be invoked from outside the scheduler.
	 * 
	 * @param newValue
	 */
	protected void setWaitingTimeSeconds(int newValue) {
		assert newValue >= 0;
		int oldValue = waitingTimeSeconds;
		if (oldValue != newValue) {
			waitingTimeSeconds = newValue;
			fireModelChanged(Attribute.WAITING_TIME_SECONDS, oldValue, newValue);
		}
	}

	/**
	 * The waiting time is only set if {@link #getStatus()} returns {@link Status#OVERLOAD}.
	 * 
	 * @return a value between {@code 0} and {@code 100}
	 */
	public int getWaitingTimePercent() {
		return waitingTimeSeconds;
	}
}
