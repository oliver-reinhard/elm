package elm.scheduler.model;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.model.Device;
import elm.scheduler.HomeServerChangeListener;
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
	 * Updates the internal device list and individual device's properties.
	 * 
	 * @param devices
	 *            cannot be {@code null}
	 * @throws UnsupportedModelException
	 *             if one of the devices does is not suitable for ELM
	 */
	void updateDeviceInfos(List<Device> devices) throws UnsupportedModelException;

	Collection<DeviceInfo> getDeviceInfos();

	DeviceInfo getDeviceInfo(String deviceId);

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
	void putDeviceUpdate(AbstractDeviceUpdate update);

	/**
	 * Device updates can be {@link #putDeviceUpdate(AbstractDeviceUpdate) put} one by one without the receiver even noticing. This method notifies all
	 * {@link HomeServerChangeListener}s of these changes, notably the {@link HomeServerManager}.
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
	void executeDeviceUpdates(HomeServerInternalApiClient client, Logger log);

	void addChangeListener(HomeServerChangeListener listener);

	void removeChangeListener(HomeServerChangeListener listener);
}
