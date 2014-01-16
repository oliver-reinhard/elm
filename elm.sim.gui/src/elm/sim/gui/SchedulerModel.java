package elm.sim.gui;

public class SchedulerModel {

	/** A simple metamodel of the {@link OutletModel}. */
	public enum Properties {
		STATUS, WAITING_TIME_PERCENT;
		
		public String id() {
			return "SCHEDULER__" + name();
		}
	}

	private final Status	status;

	/** Forecasted waiting time in percent of some maximum; {@code 0} means currently no waiting time. */
	private final int		waitingTimePercent;

	private SchedulerModel(Status status, int waitingTimePercent) {
		this.status = status;
		this.waitingTimePercent = waitingTimePercent;
	}

	/**
	 * This constructor implies {@link Status#ERROR}.
	 * 
	 * @param waitingTimePercent
	 *            must be {@code > 0} and {@code <= 100}
	 */
	public SchedulerModel(int waitingTimePercent) {
		this(Status.OVERLOAD, waitingTimePercent);
		assert waitingTimePercent > 0;
	}

	/**
	 * This constructor implies that the passed status is <em>not</em> {@link Status#ERROR} and thus that the waiting time is {@code 0}.
	 * 
	 * @param status
	 *            cannot be {@link Status#ERROR}
	 */
	public SchedulerModel(Status status) {
		this(status, 0);
		assert status != Status.ERROR;
	}

	public Status getStatus() {
		return status;
	}

	/**
	 * The waiting time is only set if {@link #getStatus()} returns {@link Status#ERROR}.
	 * 
	 * @return a value between {@code 0} and {@code 100}
	 */
	public int getWaitingTimePercent() {
		return waitingTimePercent;
	}
}
