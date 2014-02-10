package elm.scheduler;

import elm.scheduler.model.AsynchronousDeviceUpdate;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.HomeServer;

public interface HomeServerChangeListener {

	/**
	 * Notification that {@link DeviceInfo} objects have been added to or removed from the {@link HomeServer}, or that important properties of
	 * {@link DeviceInfo} objects have changed.
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param server
	 *            cannot be {@code null}
	 * @param urgent
	 *            the callee must react immediately
	 */
	void deviceInfosUpdated(HomeServer server, boolean urgent);

	/**
	 * Notification that {@link AsynchronousDeviceUpdate}s that await processing have been added to the {@link HomeServer} .
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler. Especially it should not process the
	 * {@link AsynchronousDeviceUpdate}s itself.
	 * </p>
	 * 
	 * @param server
	 *            cannot be {@code null}
	 * @param urgent
	 *            the callee must react immediately
	 */
	void deviceUpdatesPending(HomeServer server, boolean urgent);

}