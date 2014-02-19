package elm.scheduler.model.impl;

import static elm.scheduler.model.DeviceController.UpdateResult.DEVICE_STATUS_REQUIRED;
import static elm.scheduler.model.DeviceController.UpdateResult.MINOR_UPDATES;
import static elm.scheduler.model.DeviceController.UpdateResult.NO_UPDATES;
import static elm.scheduler.model.DeviceController.UpdateResult.URGENT_UPDATES;

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
import elm.scheduler.model.AsynchRemoteDeviceUpdate;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.DeviceController.UpdateResult;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.HomeServerChangeListener;
import elm.scheduler.model.RemoteDeviceUpdateClient;
import elm.scheduler.model.UnsupportedModelException;

public class HomeServerImpl implements HomeServer {

	private final String name;
	private final URI uri;
	private final String password;

	private long lastHomeServerPollTime = 0L;
	private long isAliveCheckTime = System.currentTimeMillis();
	private long pollTimeToleranceMillis = POLL_TIME_TOLERANCE_MILLIS_DEFAULT;

	private final Map<String, DeviceController> deviceControllers = new HashMap<String, DeviceController>();
	private List<AsynchRemoteDeviceUpdate> pendingUpdates;
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
	public synchronized List<String> updateDeviceControllers(List<Device> devices) throws UnsupportedModelException {
		assert devices != null;
		UpdateResult updated = NO_UPDATES;
		List<String> idsToRemove = new LinkedList<String>(deviceControllers.keySet());
		List<String> idsNeedingStatus = new ArrayList<String>();

		for (Device device : devices) {
			final String id = device.id;
			DeviceController deviceController = deviceControllers.get(id);
			// Add DeviceController for new device:
			if (deviceController == null) {
				deviceController = new DeviceControllerImpl(this, device);
				deviceControllers.put(id, deviceController);
			}
			final UpdateResult deviceControllerUpdate = deviceController.update(device);
			if (deviceControllerUpdate == DEVICE_STATUS_REQUIRED) {
				// need Status block for this device
				idsNeedingStatus.add(id);
			}
			updated = updated.and(deviceControllerUpdate);
			idsToRemove.remove(id);
		}
		if (!idsNeedingStatus.isEmpty()) {
			return idsNeedingStatus;
		}

		// Remove DeviceController for obsolete devices
		for (String id : idsToRemove) {
			deviceControllers.remove(id);
			updated = updated.and(MINOR_UPDATES);
		}
		fireDeviceControllersChanged(updated);
		return null;
	}

	@Override
	public Collection<DeviceController> getDeviceControllers() {
		return deviceControllers.values();
	}

	@Override
	public DeviceController getDeviceController(String id) {
		return deviceControllers.get(id);
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
		// Either the home server has been polled since the last isAlive inquiry, or this inquiry is no later than POLL_TIME_TOLERANCE_MILLIS after the last
		// poll
		return oldIsAliveCheckTime <= lastHomeServerPollTime || lastHomeServerPollTime + pollTimeToleranceMillis >= isAliveCheckTime;
	}

	@Override
	public synchronized void putDeviceUpdate(AsynchRemoteDeviceUpdate update) {
		assert update != null;
		if (pendingUpdates == null) {
			pendingUpdates = new ArrayList<AsynchRemoteDeviceUpdate>();
		}
		pendingUpdates.add(update);
	}

	/**
	 * Used for testing.
	 */
	public synchronized List<AsynchRemoteDeviceUpdate> getPendingUpdates() {
		return pendingUpdates == null ? null : Collections.unmodifiableList(pendingUpdates);
	}

	@Override
	public void executeRemoteDeviceUpdates(RemoteDeviceUpdateClient client, Logger log) {
		assert client != null;
		assert log != null;
		List<AsynchRemoteDeviceUpdate> updates;
		// we don't want to hold the lock during the update execution
		synchronized (this) {
			if (pendingUpdates == null) {
				return;
			}
			updates = pendingUpdates;
			pendingUpdates = null;
		}
		for (AsynchRemoteDeviceUpdate update : updates) {
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
		synchronized (listeners) {
			if (!listeners.contains(listener)) {
				listeners.add(listener);
			}
		}
	}

	@Override
	public void removeChangeListener(HomeServerChangeListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	private void fireDeviceControllersChanged(UpdateResult updated) {
		if (updated != NO_UPDATES) {
			synchronized (listeners) {
				// The device-manager updates MUST NOT BE long-lasting or blocking!
				for (HomeServerChangeListener listener : listeners) {
					listener.devicesControllersUpdated(this, updated == URGENT_UPDATES);
				}
			}
		}
	}

	public void fireDeviceChangesPending() {
		// lock "this" as shortly as possible, particularly don't notify listeners:
		boolean doUpdate = false;
		boolean urgent = false;
		synchronized (this) {
			if (pendingUpdates != null) {
				doUpdate = true;
				for (AsynchRemoteDeviceUpdate update : pendingUpdates) {
					urgent = urgent || update.isUrgent();
				}
			}
			// now release the lock
		}
		if (doUpdate) {
			synchronized (listeners) {
				// The device updates MUST NOT BE long-lasting or blocking!
				for (HomeServerChangeListener listener : listeners) {
					listener.deviceUpdatesPending(this, urgent);
				}
			}
		}
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer(getName());
		b.append("[");
		int n = getDeviceControllers().size();
		int i = 1;
		for (DeviceController di : getDeviceControllers()) {
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
