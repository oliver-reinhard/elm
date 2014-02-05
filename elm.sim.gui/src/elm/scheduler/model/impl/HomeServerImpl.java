package elm.scheduler.model.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.model.Device;
import elm.scheduler.HomeServerChangeListener;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.DeviceInfo.UpdateResult;
import elm.scheduler.model.AbstractDeviceUpdate;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.UnsupportedModelException;

public class HomeServerImpl implements HomeServer {

	private final String name;
	private final URI uri;
	private final String password;
	private long lastHomeServerPollTime = 0L;
	private long isAliveCheckTime = 0L;
	private final Map<String, DeviceInfo> deviceInfos = new HashMap<String, DeviceInfo>();
	private List<AbstractDeviceUpdate> pendingUpdates;
	private final Logger log = Logger.getLogger(getClass().getName());
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
	public void updateDeviceInfos(List<Device> devices) throws UnsupportedModelException {
		assert devices != null;
		UpdateResult updated = UpdateResult.NO_UPDATES;
		List<String> idsToRemove = new LinkedList<String>(deviceInfos.keySet());
		for (Device device : devices) {
			final String id = device.id;
			DeviceInfo info = deviceInfos.get(id);
			// Add DeviceInfo for new devices:
			if (info == null) {
				info = new DeviceInfoImpl(this, device);
				deviceInfos.put(id, info);
				updated = updated.and(UpdateResult.MINOR_UPDATES);
			} else {
				updated = updated.and(info.update(device));
			}
			idsToRemove.remove(id);
		}

		// Remove DeviceInfo for obsolete devices
		for (String id : idsToRemove) {
			deviceInfos.remove(id);
			updated = updated.and(UpdateResult.MINOR_UPDATES);
		}
		fireDeviceInfosChanged(updated);
	}

	@Override
	public Collection<DeviceInfo> getDevicesInfos() {
		return deviceInfos.values();
	}

	@Override
	public void updateLastHomeServerPollTime() {
		this.lastHomeServerPollTime = System.currentTimeMillis();
	}

	@Override
	public boolean isAlive() {
		boolean result = lastHomeServerPollTime > isAliveCheckTime;
		isAliveCheckTime = System.currentTimeMillis();
		return result;
	}

	@Override
	public synchronized void putDeviceUpdate(AbstractDeviceUpdate update) {
		assert update != null;
		if (pendingUpdates == null) {
			pendingUpdates = new ArrayList<AbstractDeviceUpdate>();
		}
		pendingUpdates.add(update);
	}

	@Override
	public void executeDeviceUpdates(HomeServerInternalApiClient client) {
		assert client != null;
		List<AbstractDeviceUpdate> updates;
		// we don't want to hold the lock during the update execution
		synchronized (this) {
			if (pendingUpdates == null) {
				return;
			}
			updates = pendingUpdates;
			pendingUpdates = null;
		}
		for (AbstractDeviceUpdate update : updates) {
			try {
				update.run(client);
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
		if (updated != UpdateResult.NO_UPDATES) {
			for (HomeServerChangeListener listener : listeners) {
				listener.deviceInfosUpdated(this, updated == UpdateResult.URGENT_UPDATES);
			}
		}
	}

	public void fireDeviceChangesPending() {
		if (pendingUpdates != null) {
			boolean urgent = false;
			for (AbstractDeviceUpdate update : pendingUpdates) {
				urgent = urgent || update.isUrgent();
			}
			for (HomeServerChangeListener listener : listeners) {
				listener.deviceUpdatesPending(this, urgent);
			}
		}
	}
}
