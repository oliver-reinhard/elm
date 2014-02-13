package elm.sim.model.impl;

import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.Scheduler;
import elm.sim.model.Status;

public class SchedulerImpl extends AbstractSimObject implements Scheduler {

	public static final int SIMULATED_WAITING_TIME_SECONDS = 10;

	private Status status = Status.OFF;

	/** Forecasted waiting time in seconds; {@code 0} means currently no waiting time. */
	private int waitingTimeSeconds = NO_WAITING_TIME;

	/** Simulate the waiting time countdown. */
	private WaitingTimeCountdown waitingTimeCountdown = new WaitingTimeCountdown(this);

	@Override
	public String getLabel() {
		return "Scheduler";
	}

	@Override
	public SimAttribute[] getSimAttributes() {
		return Attribute.values();
	}

	@Override
	public void setStatus(Status newValue) {
		assert newValue != null;
		Status oldValue = status;
		if (oldValue != newValue) {
			status = newValue;
			fireModelChanged(Attribute.STATUS, oldValue, newValue);
			if (newValue == Status.OVERLOAD) {
				waitingTimeCountdown.start(SIMULATED_WAITING_TIME_SECONDS);
			} else {
				waitingTimeCountdown.stop();
			}
		}
	}

	@Override
	public Status getStatus() {
		return status;
	}

	/**
	 * This method must only be invoked by the framework.
	 * 
	 * @param newValue
	 *            must be {@code >= 0}
	 */
	public void setWaitingTimeSeconds(int newValue) {
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
	@Override
	public int getWaitingTimePercent() {
		return waitingTimeSeconds;
	}
}
