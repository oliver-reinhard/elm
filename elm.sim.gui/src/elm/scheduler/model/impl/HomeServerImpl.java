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
import elm.hs.api.model.ElmStatus;
import elm.hs.api.model.ElmUserFeedback;
import elm.scheduler.ElmTimeService;
import elm.scheduler.ElmUserFeedbackManager;
import elm.scheduler.model.RemoteDeviceUpdate;
import elm.scheduler.model.DeviceController;
import elm.scheduler.model.DeviceController.UpdateResult;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.HomeServerChangeListener;
import elm.scheduler.model.RemoteDeviceUpdateClient;
import elm.scheduler.model.UnsupportedDeviceModelException;

public class HomeServerImpl implements HomeServer {

	private final String name;
	private final URI uri;
	private final String password;
	private final ElmUserFeedbackManager userFeedbackManager;

	/** Enable deterministic testing via a replacement of this time service. */
	private ElmTimeService timeService = ElmTimeService.INSTANCE;
	
	private long lastHomeServerPollTime = 0L;
	private long isAliveCheckTime = timeService.currentTimeMillis();
	private long pollTimeToleranceMillis = POLL_TIME_TOLERANCE_MILLIS_DEFAULT;

	private final Map<String, DeviceController> deviceControllers = new HashMap<String, DeviceController>();
	private List<RemoteDeviceUpdate> pendingUpdates;
	private List<HomeServerChangeListener> listeners = new ArrayList<HomeServerChangeListener>();

	public HomeServerImpl(URI uri, String password, ElmUserFeedbackManager userFeedbackManager) {
		this(uri, password, null, userFeedbackManager);
	}

	public HomeServerImpl(URI uri, String password, String name, ElmUserFeedbackManager userFeedbackManager) {
		assert uri != null;
		assert password != null && !password.isEmpty();
		assert userFeedbackManager != null;
		this.uri = uri;
		this.password = password;
		this.name = name;
		this.userFeedbackManager = userFeedbackManager;
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

	/** Used for testing. */
	public ElmUserFeedbackManager getUserFeedbackManager() {
		return userFeedbackManager;
	}

	@Override
	public synchronized List<String> updateDeviceControllers(List<Device> devices) throws UnsupportedDeviceModelException {
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
				((DeviceControllerImpl) deviceController).setTimeService(timeService);
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

	/** Used for testing. */
	public void setTimeService(ElmTimeService timeService) {
		assert timeService != null;
		this.timeService = timeService;
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
		this.lastHomeServerPollTime = timeService.currentTimeMillis();
	}

	@Override
	public boolean isAlive() {
		long oldIsAliveCheckTime = isAliveCheckTime;
		isAliveCheckTime = timeService.currentTimeMillis();
		// Either the home server has been polled since the last isAlive inquiry, or this inquiry is no later than POLL_TIME_TOLERANCE_MILLIS after the last
		// poll
		return oldIsAliveCheckTime <= lastHomeServerPollTime || lastHomeServerPollTime + pollTimeToleranceMillis >= isAliveCheckTime;
	}

	@Override
	public synchronized void putDeviceUpdate(RemoteDeviceUpdate update) {
		assert update != null;
		if (pendingUpdates == null) {
			pendingUpdates = new ArrayList<RemoteDeviceUpdate>();
		}
		pendingUpdates.add(update);
	}

	/**
	 * Used for testing.
	 */
	public synchronized List<RemoteDeviceUpdate> getPendingUpdates() {
		return pendingUpdates == null ? null : Collections.unmodifiableList(pendingUpdates);
	}

	@Override
	public void executeRemoteDeviceUpdates(RemoteDeviceUpdateClient client, Logger log) {
		assert client != null;
		assert log != null;
		List<RemoteDeviceUpdate> updates;
		// we don't want to hold the lock during the update execution
		synchronized (this) {
			if (pendingUpdates == null) {
				return;
			}
			updates = pendingUpdates;
			pendingUpdates = null;
		}
		for (RemoteDeviceUpdate update : updates) {
			try {
				update.execute(client, log);
			} catch (Exception e) {
				log.log(Level.SEVERE, "Remote device update failed", e);
			}
		}
	}

	@Override
	public void dispatchElmUserFeedback(String deviceId, ElmStatus deviceStatus, int expectedWaitingTimeMillis) {
		userFeedbackManager.putFeedback(new ElmUserFeedback(deviceId, deviceStatus, expectedWaitingTimeMillis));
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

	public void fireDeviceUpdatesPending() {
		if (pendingUpdates != null) {
			synchronized (listeners) {
				// The device updates MUST NOT BE long-lasting or blocking!
				for (HomeServerChangeListener listener : listeners) {
					listener.deviceUpdatesPending(this);
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
