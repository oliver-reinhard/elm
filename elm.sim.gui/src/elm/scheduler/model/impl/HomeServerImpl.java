package elm.scheduler.model.impl;

import static elm.scheduler.model.DeviceManager.UpdateResult.DEVICE_STATUS_REQUIRED;
import static elm.scheduler.model.DeviceManager.UpdateResult.MINOR_UPDATES;
import static elm.scheduler.model.DeviceManager.UpdateResult.NO_UPDATES;
import static elm.scheduler.model.DeviceManager.UpdateResult.URGENT_UPDATES;

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
import elm.scheduler.model.DeviceManager;
import elm.scheduler.model.DeviceManager.UpdateResult;
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
	
	private final Map<String, DeviceManager> deviceManagers = new HashMap<String, DeviceManager>();
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
	public List<String> updateDeviceManagers(List<Device> devices) throws UnsupportedModelException {
		assert devices != null;
		UpdateResult updated = NO_UPDATES;
		List<String> idsToRemove = new LinkedList<String>(deviceManagers.keySet());
		List<String> idsNeedingStatus = new ArrayList<String>();
		
		for (Device device : devices) {
			final String id = device.id;
			DeviceManager deviceManager = deviceManagers.get(id);
			// Add DeviceManager for new device:
			if (deviceManager == null) {
				deviceManager = new DeviceManagerImpl(this, device);
				deviceManagers.put(id, deviceManager);
			} 
			final UpdateResult deviceManagerUpdate = deviceManager.update(device);
			if (deviceManagerUpdate == DEVICE_STATUS_REQUIRED) {
				// need Status block for this device
				idsNeedingStatus.add(id);
			}
			updated = updated.and(deviceManagerUpdate);
			idsToRemove.remove(id);
		}
		if (!idsNeedingStatus.isEmpty()) {
			return idsNeedingStatus;
		}

		// Remove DeviceManager for obsolete devices
		for (String id : idsToRemove) {
			deviceManagers.remove(id);
			updated = updated.and(MINOR_UPDATES);
		}
		fireDeviceManagersChanged(updated);
		return null;
	}

	@Override
	public Collection<DeviceManager> getDeviceManagers() {
		return deviceManagers.values();
	}

	@Override
	public DeviceManager getDeviceManager(String id) {
		return deviceManagers.get(id);
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

	private void fireDeviceManagersChanged(UpdateResult updated) {
		if (updated != NO_UPDATES) {
			// The device-manager updates MUST NOT BE long-lasting or blocking!
			for (HomeServerChangeListener listener : listeners) {
				listener.devicesManagersUpdated(this, updated == URGENT_UPDATES);
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
		int n = getDeviceManagers().size();
		int i = 1;
		for (DeviceManager di : getDeviceManagers()) {
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
