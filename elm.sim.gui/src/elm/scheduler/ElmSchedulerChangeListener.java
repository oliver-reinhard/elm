package elm.scheduler;

import elm.hs.api.model.ElmStatus;

/**
 * <p>
 * <em>Note: </em>Scheduler state changes are notified by the {@link ElmScheduler} <em>synchronously</em> on its processing thread. Implementors of this
 * interface <em>must not</em> do time-consuming or non-deterministic processing.
 * </p>
 */
public interface ElmSchedulerChangeListener {

	/**
	 * The status of the scheduler has changed.
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param oldStatus
	 *            never {@code null}
	 * @param newStatus
	 *            never {@code null}
	 */
	void statusChanged(ElmStatus oldStatus, ElmStatus newStatus);

	/**
	 * The total demand power requested by the devices has changed.
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param oldPowerWatt
	 * @param newPowerWatt
	 */
	void totalDemandPowerChanged(int oldPowerWatt, int newPowerWatt);

}
