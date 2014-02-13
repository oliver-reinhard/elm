package elm.scheduler;

import elm.ui.api.ElmStatus;

/**
 * <p>
 * <em>Note: </em>Scheduler state changes are notified by the {@link Scheduler} <em>synchronously</em> on its processing thread. Implementors of this interface
 * <em>must not</em> do time-consuming or non-deterministic processing.
 * </p>
 */
public interface SchedulerChangeListener {

	/**
	 * The status of the scheduler has changed.
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param oldStatus
	 * @param newStatus
	 */
	void statusChanged(ElmStatus oldStatus, ElmStatus newStatus);

}
