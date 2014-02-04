package elm.scheduler.model;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.model.Device;
import elm.scheduler.HomeServerChangeListener;

public interface HomeServer {

	URI getUri();

	String getPassword();

	/**
	 * Updates the internal device list and individual device's properties.
	 * 
	 * @param devices
	 *            cannot be {@code null}
	 */
	void updateDeviceInfos(List<Device> devices);

	Collection<DeviceInfo> getDevicesInfos();

	void updateLastHomeServerPollTime();

	/**
	 * Returns {@code true} if the physical Home Server has been contacted since the last invocation of {@link #isAlive()}.
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
	void putDeviceUpdate(DeviceUpdate update);
	
	void fireDeviceChangesPending();

	/**
	 * Executes pending updates.
	 * 
	 * @param client
	 *            cannot be {@code null}
	 */
	void executeDeviceUpdates(HomeServerInternalApiClient client);

	void addChangeListener(HomeServerChangeListener listener);

	void removeChangeListener(HomeServerChangeListener listener);
}
