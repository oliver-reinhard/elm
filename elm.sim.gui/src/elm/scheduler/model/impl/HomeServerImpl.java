package elm.scheduler.model.impl;

import static elm.scheduler.model.DeviceInfo.UpdateResult.DEVICE_STATUS_REQUIRED;
import static elm.scheduler.model.DeviceInfo.UpdateResult.MINOR_UPDATES;
import static elm.scheduler.model.DeviceInfo.UpdateResult.NO_UPDATES;
import static elm.scheduler.model.DeviceInfo.UpdateResult.URGENT_UPDATES;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.model.Device;
import elm.scheduler.model.AsynchronousPhysicalDeviceUpdate;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.DeviceInfo.UpdateResult;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.HomeServerChangeListener;
import elm.scheduler.model.PhysicalDeviceUpdateClient;
import elm.scheduler.model.UnsupportedModelException;

public class HomeServerImpl implements HomeServer {

	private final String name;
	private final URI uri;
	private final String password;
	
	private long lastHomeServerPollTime = 0L;
	private long isAliveCheckTime = System.currentTimeMillis();
	private long pollTimeToleranceMillis = POLL_TIME_TOLERANCE_MILLIS_DEFAULT;
	
	private final Map<String, DeviceInfo> deviceInfos = new HashMap<String, DeviceInfo>();
	private List<AsynchronousPhysicalDeviceUpdate> pendingUpdates;
	private List<HomeServerChangeListener> listeners = new ArrayList<HomeServerChangeListener>();

	public HomeServerImpl(URI uri, String password) {
		this(uri, password, null);
	}

	public HomeServerImpl(URI uri, String password, String name) {
		assert uri != null;
		assert password != null && !password.isEmpty();
		this.uri = uri;
		this.password = password;
		this.name = name;
	}

	public String getName() {
		return name;
	}

	@Override
	public URI getUri() {
		return uri;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public List<String> updateDeviceInfos(List<Device> devices) throws UnsupportedModelException {
		assert devices != null;
		UpdateResult updated = NO_UPDATES;
		List<String> idsToRemove = new LinkedList<String>(deviceInfos.keySet());
		List<String> idsNeedingStatus = new ArrayList<String>();
		
		for (Device device : devices) {
			final String id = device.id;
			DeviceInfo deviceInfo = deviceInfos.get(id);
			// Add DeviceInfo for new devices:
			if (deviceInfo == null) {
				deviceInfo = new DeviceInfoImpl(this, device);
				deviceInfos.put(id, deviceInfo);
			} 
			final UpdateResult deviceInfoUpdate = deviceInfo.update(device);
			if (deviceInfoUpdate == DEVICE_STATUS_REQUIRED) {
				// need Status block for this device
				idsNeedingStatus.add(id);
			}
			updated = updated.and(deviceInfoUpdate);
			idsToRemove.remove(id);
		}
		if (!idsNeedingStatus.isEmpty()) {
			return idsNeedingStatus;
		}

		// Remove DeviceInfo for obsolete devices
		for (String id : idsToRemove) {
			deviceInfos.remove(id);
			updated = updated.and(MINOR_UPDATES);
		}
		fireDeviceInfosChanged(updated);
		return null;
	}

	@Override
	public Collection<DeviceInfo> getDeviceInfos() {
		return deviceInfos.values();
	}

	@Override
	public DeviceInfo getDeviceInfo(String id) {
		return deviceInfos.get(id);
	}

	@Override
	public long getPollTimeToleranceMillis() {
		return pollTimeToleranceMillis;
	}

	@Override
	public void setPollTimeToleranceMillis(long pollTimeToleranceMillis) {
		this.pollTimeToleranceMillis = pollTimeToleranceMillis;
	}

	@Override
	public void updateLastHomeServerPollTime() {
		this.lastHomeServerPollTime = System.currentTimeMillis();
	}

	@Override
	public boolean isAlive() {
		long oldIsAliveCheckTime = isAliveCheckTime;
		isAliveCheckTime = System.currentTimeMillis();
		// Either the home server has been polled since the last isAlive inquiry, or this inquiry is no later than POLL_TIME_TOLERANCE_MILLIS after the last poll
		return oldIsAliveCheckTime <= lastHomeServerPollTime || lastHomeServerPollTime + pollTimeToleranceMillis >= isAliveCheckTime;
	}

	@Override
	public synchronized void putDeviceUpdate(AsynchronousPhysicalDeviceUpdate update) {
		assert update != null;
		if (pendingUpdates == null) {
			pendingUpdates = new ArrayList<AsynchronousPhysicalDeviceUpdate>();
		}
		pendingUpdates.add(update);
	}
	
	/**
	 * Used for testing.
	 */
	public List<AsynchronousPhysicalDeviceUpdate> getPendingUpdates() {
		return pendingUpdates == null ? null : Collections.unmodifiableList(pendingUpdates);
	}

	@Override
	public void executePhysicalDeviceUpdates(PhysicalDeviceUpdateClient client, Logger log) {
		assert client != null;
		assert log != null;
		List<AsynchronousPhysicalDeviceUpdate> updates;
		// we don't want to hold the lock during the update execution
		synchronized (this) {
			if (pendingUpdates == null) {
				return;
			}
			updates = pendingUpdates;
			pendingUpdates = null;
		}
		for (AsynchronousPhysicalDeviceUpdate update : updates) {
			try {
				update.execute(client, log);
			} catch (Exception e) {
				log.log(Level.SEVERE, "Device update failed", e);
			}
		}
	}

	@Override
	public void addChangeListener(HomeServerChangeListener listener) {
		assert listener != null;
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeChangeListener(HomeServerChangeListener listener) {
		listeners.remove(listener);
	}

	private void fireDeviceInfosChanged(UpdateResult updated) {
		if (updated != NO_UPDATES) {
			// The device info updates MUST NOT BE long-lasting or blocking!
			for (HomeServerChangeListener listener : listeners) {
				listener.deviceInfosUpdated(this, updated == URGENT_UPDATES);
			}
		}
	}

	public void fireDeviceChangesPending() {
		if (pendingUpdates != null) {
			boolean urgent = false;
			for (AsynchronousPhysicalDeviceUpdate update : pendingUpdates) {
				urgent = urgent || update.isUrgent();
			}
			// The device updates MUST NOT BE long-lasting or blocking!
			for (HomeServerChangeListener listener : listeners) {
				listener.deviceUpdatesPending(this, urgent);
			}
		}
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer(getName());
		b.append("[");
		int n = getDeviceInfos().size();
		int i = 1;
		for (DeviceInfo di : getDeviceInfos()) {
			b.append(di.toString());
			if (i < n) {
				b.append(", ");
			}
			i++;
		}
		b.append("]");
		return b.toString();
	}
}
