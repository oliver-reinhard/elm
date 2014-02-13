package elm.scheduler.model;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import elm.hs.api.model.Device;
import elm.hs.api.model.Info;
import elm.hs.api.model.Status;
import elm.scheduler.HomeServerManager;

public interface HomeServer {

	/** {@link #isAlive()} will return {@code true} until this duration after the last {@link #updateLastHomeServerPollTime()} invocation. */
	static final long POLL_TIME_TOLERANCE_MILLIS_DEFAULT = 2000;

	/**
	 * Optional.
	 * 
	 * @return may be {@code null}
	 */
	String getName();

	URI getUri();

	String getPassword();

	/**
	 * Updates the internal device list and individual devices' properties. Devices that are not yet known to the {@link HomeServer} are added to its internal
	 * list of devices; devices absent from the list are removed from the internal structures.
	 * <p>
	 * <em>Note: </em>Typically the {@code devices} passed to the method only contain an {@link Info} but <em>NOT</em> the {@link Status} block. However, the
	 * {@link Status} block is required in some situations to make relevant decisions. The returned list of device IDs asks for more detailed device information
	 * including the {@link Status} block.
	 * </p>
	 * 
	 * @param devices
	 *            cannot be {@code null}
	 * @return list of device IDs for which to pass more detailed information immediately; can be {@code null}
	 * @throws UnsupportedModelException
	 *             if one of the devices does is not suitable for ELM
	 */
	List<String> updateDeviceManagers(List<Device> devices) throws UnsupportedModelException;

	Collection<DeviceManager> getDeviceManagers();

	DeviceManager getDeviceManager(String deviceId);

	void setPollTimeToleranceMillis(long pollTimeToleranceMillis);

	long getPollTimeToleranceMillis();

	void updateLastHomeServerPollTime();

	/**
	 * Returns {@code true} if the physical Home Server has been contacted since the last invocation of {@link #isAlive()} or if the last invocation of
	 * {@link #updateLastHomeServerPollTime()} was not earlier than {@link #getPollTimeToleranceMillis()} ago.
	 * <p>
	 * <em>Note: </em>that the invocation of this method has the side effect or storing the invocation time.</em>
	 * 
	 * @return
	 */
	boolean isAlive();

	/**
	 * Buffers a device update without executing them. This constitutes an asynchronous communication mechanism between the caller and a {@link HomeServer}.
	 * <p>
	 * <em>Note: </em> This method should only be executed by the scheduler.
	 * </p>
	 * 
	 * @param updates
	 *            cannot be {@code null}
	 */
	void putDeviceUpdate(AsynchronousPhysicalDeviceUpdate update);

	/**
	 * Device updates can be {@link #putDeviceUpdate(AsynchronousPhysicalDeviceUpdate) put} one by one without the receiver even noticing. This method notifies all
	 * {@link HomeServerChangeListener}s of these changes, notably the {@link HomeServerManager}.
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 */
	void fireDeviceChangesPending();

	/**
	 * Executes pending updates.
	 * 
	 * @param client
	 *            cannot be {@code null}
	 * @param log
	 *            never {@code null}
	 */
	void executePhysicalDeviceUpdates(PhysicalDeviceUpdateClient client, Logger log);

	/**
	 * Adds a listener.
	 * <p>
	 * <em>Note: </em>Method implementations of the listener must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param listener
	 *            cannot be {@code null}
	 */
	void addChangeListener(HomeServerChangeListener listener);

	void removeChangeListener(HomeServerChangeListener listener);
}
