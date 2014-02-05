package elm.scheduler;


/**
 * <p>
 * <em>Note: </em>Scheduler state changes are notified by the {@link Scheduler} <em>synchronously</em> on its processing thread. Implementors of this interface
 * <em>must not</em> do time-consuming or non-deterministic processing.
 * </p>
 */
public interface SchedulerChangeListener {

	void statusChanged(ElmStatus oldStatus, ElmStatus newStatus);

}
