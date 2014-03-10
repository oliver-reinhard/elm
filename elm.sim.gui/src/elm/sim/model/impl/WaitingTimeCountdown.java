package elm.sim.model.impl;

import elm.sim.model.SimpleScheduler;
import elm.sim.model.SimStatus;

/**
 * Counts down the waiting time of a scheduler in steps (of a {@link #NOTIFICATION_INTERVAL_MILLIS given duration}) and sets the remaining waiting time value of
 * the scheduler. If the countdown is not interrupted (i.e. stopped), it will set the scheduler's status to {@link #FOLLOW_UP_STATUS} when the countdown ends,
 * else it will no alter the scheduler status.
 */
public class WaitingTimeCountdown implements Runnable {

	private static final SimStatus FOLLOW_UP_STATUS = SimStatus.SATURATION;

	public static final int NOTIFICATION_INTERVAL_MILLIS = 1000;

	private final SimpleSchedulerImpl model;
	private Thread running;
	private boolean shouldStop;
	private int waitingTimeSeconds;

	public WaitingTimeCountdown(SimpleSchedulerImpl model) {
		assert model != null;
		this.model = model;
	}

	/**
	 * This method can only be called when the {@link #model scheduler} is in status {@link SimStatus#OVERLOAD}.
	 * 
	 * @param waitingTimeSeconds
	 *            must be {@code > >}
	 */
	public synchronized void start(int waitingTimeSeconds) {
		assert waitingTimeSeconds > 0;
		assert model.getStatus() == SimStatus.OVERLOAD;
		this.waitingTimeSeconds = waitingTimeSeconds;
		stop();
		running = new Thread(this, "Waiting Time Simulator");
		shouldStop = false;
		running.start();
	}

	public synchronized void stop() {
		if (running != null) {
			shouldStop = true;
			this.notify(); // breaks the loop
			running = null;
		}
	}

	@Override
	public synchronized void run() {
		for (; !shouldStop && waitingTimeSeconds > 0;) {
			try {
				model.updateWaitingTimeSeconds(waitingTimeSeconds);
				wait(NOTIFICATION_INTERVAL_MILLIS);
			} catch (InterruptedException e) {
				break; // ends the loop
			}
			waitingTimeSeconds--;
		}
		model.updateWaitingTimeSeconds(SimpleScheduler.NO_WAITING_TIME);
		if (waitingTimeSeconds <= 0) { // timer was NOT interrupted / stopped
			model.setStatus(FOLLOW_UP_STATUS);
		}
		running = null;
	}
}