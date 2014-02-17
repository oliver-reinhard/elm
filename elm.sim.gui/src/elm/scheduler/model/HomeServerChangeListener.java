package elm.scheduler.model;


public interface HomeServerChangeListener {

	/**
	 * Notification that {@link DeviceManager} objects have been added to or removed from the {@link HomeServer}, or that important properties of
	 * {@link DeviceManager} objects have changed.
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param server
	 *            cannot be {@code null}
	 * @param urgent
	 *            the callee must react immediately
	 */
	void devicesManagersUpdated(HomeServer server, boolean urgent);

	/**
	 * Notification that {@link AsynchRemoteDeviceUpdate}s that await processing have been added to the {@link HomeServer} .
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler. Especially it should not process the
	 * {@link AsynchRemoteDeviceUpdate}s itself.
	 * </p>
	 * 
	 * @param server
	 *            cannot be {@code null}
	 * @param urgent
	 *            the callee must react immediately
	 */
	void deviceUpdatesPending(HomeServer server, boolean urgent);

}