package elm.sim.model.impl;

import elm.sim.metamodel.AbstractSimObject;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.SimScheduler;
import elm.sim.model.SimStatus;

public class SimSchedulerImpl extends AbstractSimObject implements SimScheduler {

	public static final int SIMULATED_WAITING_TIME_SECONDS = 10;

	private SimStatus status = SimStatus.OFF;

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
	public void setStatus(SimStatus newValue) {
		assert newValue != null;
		SimStatus oldValue = status;
		if (oldValue != newValue) {
			status = newValue;
			fireModelChanged(Attribute.STATUS, oldValue, newValue);
			if (newValue == SimStatus.OVERLOAD) {
				waitingTimeCountdown.start(SIMULATED_WAITING_TIME_SECONDS);
			} else {
				waitingTimeCountdown.stop();
			}
		}
	}

	@Override
	public SimStatus getStatus() {
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
	 * The waiting time is only set if {@link #getStatus()} returns {@link SimStatus#OVERLOAD}.
	 * 
	 * @return a value between {@code 0} and {@code 100}
	 */
	@Override
	public int getWaitingTimePercent() {
		return waitingTimeSeconds;
	}
}
