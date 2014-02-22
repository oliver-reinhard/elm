package elm.scheduler;

import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.client.HomeServerPublicApiClient;
import elm.hs.api.model.Device;
import elm.hs.api.model.ElmUserFeedback;
import elm.hs.api.model.HomeServerResponse;
import elm.hs.api.model.Info;
import elm.hs.api.model.Status;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.HomeServerChangeListener;
import elm.scheduler.model.RemoteDeviceUpdateClient;
import elm.scheduler.model.UnsupportedModelException;
import elm.util.ClientException;
import elm.util.ClientUtil;

/**
 * This controller is responsible for the connection to and the communication with a single Home Server server via HTTP and with the {@link Scheduler}. However,
 * it does not communicate to the {@link Scheduler} directly, but indirectly via a {@link HomeServer} object.
 * <p>
 * At each poll of the Home Server server, this controller updates an 'alive' flag at its {@link HomeServer}. This enables a separate thread to monitor the
 * health of this controller.
 * </p>
 */
public class HomeServerController implements Runnable, HomeServerChangeListener {

	private static final int DEFAULT_POLLING_INTERVAL_MILLIS = 1000;

	public enum State {
		NOT_CONNECTED, OK, TIMEOUT, STOPPED, ERROR
	}

	private enum Event {
		WAIT, POLL_HOME_SERVER, PROCESS_DEVICE_UPDATES, STOP
	}

	private final AbstractScheduler scheduler;
	private final ElmUserFeedbackManager userFeedbackManager;
	private final HomeServer homeServer;
	private State state = State.NOT_CONNECTED;

	private HomeServerPublicApiClient publicClient = null;
	private HomeServerInternalApiClient internalClient = null;
	private RemoteDeviceUpdateClient deviceUpdateClient = null;

	private ClientException lastClientException = null;
	private Event event = Event.POLL_HOME_SERVER;
	private Thread runner;

	private int pollingFailureCount = 0;
	private int pollingIntervalMillis = DEFAULT_POLLING_INTERVAL_MILLIS;
	private final Logger log = Logger.getLogger(getClass().getName());

	/**
	 * @param scheduler
	 *            cannot be {@code null}
	 * @param userFeedbackManager
	 *            must be started, cannot be {@code null}
	 * @param homeServer
	 *            cannot be {@code null}
	 */
	public HomeServerController(AbstractScheduler scheduler, ElmUserFeedbackManager userFeedbackManager, HomeServer homeServer) {
		assert scheduler != null;
		assert userFeedbackManager != null;
		assert homeServer != null;
		this.scheduler = scheduler;
		this.userFeedbackManager = userFeedbackManager;
		this.homeServer = homeServer;
		this.homeServer.addChangeListener(this);
	}

	public int getPollingIntervalMillis() {
		return pollingIntervalMillis;
	}

	public void setPollingIntervalMillis(int pollingIntervalMillis) {
		this.pollingIntervalMillis = pollingIntervalMillis;
	}

	public State getState() {
		return state;
	}

	private void setState(State newState) {
		if (newState != state) {
			log(Level.INFO, "state " + state + " -> " + newState, null);
			this.state = newState;
		}
	}

	public synchronized void start() throws URISyntaxException {
		stop();
		publicClient = new HomeServerPublicApiClient(homeServer.getUri(), homeServer.getPassword());
		ClientUtil.initSslContextFactory(publicClient.getClient());

		internalClient = new HomeServerInternalApiClient(homeServer.getUri(), "admin", homeServer.getPassword(), publicClient);
		// internalClient = new HomeServerInternalApiClient(homeServer.getPassword(), publicClient);
		// ClientUtil.initSslContextFactory(internalClient.getClient());

		deviceUpdateClient = new RemoteDeviceUpdateClient() {

			@Override
			public Short setScaldProtectionTemperature(String deviceID, int newTemp) throws ClientException {
				return internalClient.setScaldProtectionTemperature(deviceID, newTemp);
			}

			@Override
			public void clearScaldProtection(String deviceID, Integer previousTemp) throws ClientException {
				internalClient.clearScaldProtection(deviceID, previousTemp);
			}

			@Override
			public void updateUserFeedback(ElmUserFeedback feedback) throws ClientException {
				if (feedback.id != null) {
					HomeServerPublicApiClient client = userFeedbackManager.getFeedackClient(feedback.id);
					client.updateUserFeedback(feedback);
				} else {
					publicClient.updateUserFeedback(feedback);
				}
			}
		};
		setState(State.OK);
		runner = new Thread(this, HomeServerController.class.getSimpleName());
		event = Event.POLL_HOME_SERVER;
		runner.start();
	}

	public synchronized void stop() {
		if (runner != null) {
			event = Event.STOP;
			this.notify(); // ends the "run()" loop
		}
	}

	@Override
	public void run() {
		try {
			try {
				publicClient.start();
				internalClient.start();
				scheduler.addHomeServer(homeServer);
				// Feedback management
				if (publicClient.supportsUserFeedback()) {
					HomeServerResponse feedbackDevicesResponse = publicClient.getFeedbackDevices();
					userFeedbackManager.addFeedbackServer(publicClient, feedbackDevicesResponse.feeback.deviceIds);
				}
			} catch (Exception e) {
				log(Level.SEVERE, "Cannot start HTTP client", e);
				setState(State.ERROR);
				return;
			}

			eventLoop(); // throws InterruptedException

		} catch (InterruptedException e) {
			// do nothing, we have already exited the event loop
		} finally {
			runner = null;
			setState(State.STOPPED);
			scheduler.removeHomeServer(homeServer);
			userFeedbackManager.removeFeedbackServer(publicClient);
			try {
				if (publicClient != null && publicClient.getClient().isStarted()) {
					publicClient.stop();
				}
				if (internalClient != null && internalClient.getClient().isStarted()) {
					internalClient.stop();
				}
			} catch (Exception e) {
				log(Level.SEVERE, "Cannot stop HTTP client", e);
			}
		}
	}

	/**
	 * This is the controller's -- a priori infinite -- event loop. It fulfills several important requirments:
	 * <ul>
	 * <li>poll the actual home server; this is a potentially long-lasting network call</li>
	 * <li>change the physical device parameters; ; this is a potentially long-lasting network call</li>
	 * <li>exit the event loop on user request or thread wait interrupt</li>
	 * <li>minimize the time spent in {@code synchronized} blocks</li>
	 * <li>maintain home-server polling interval</li>
	 * </ul>
	 * <p>
	 * <em>Note: </em>The time spent in {@code synchronized} blocks must be kept short so as not to cause delays to callers of {@code synchronized} methods of
	 * this class, most importantly the {@link Scheduler}.
	 * </p>
	 * 
	 * @throws InterruptedException
	 *             on thread interrupt
	 */
	private void eventLoop() throws InterruptedException {
		long waitIntervalMillis = pollingIntervalMillis;
		long pollingCycleStartTime = System.currentTimeMillis();

		loop: while (true) {

			if (event == Event.POLL_HOME_SERVER) {
				log(Level.FINE, "poll devices", null);
				pollHomeServer(); // this may take many milliseconds and 'event' could change in the meantime
				pollingCycleStartTime = System.currentTimeMillis();
				synchronized (this) {
					if (event == Event.STOP) {
						break loop;
					}
					if (event == Event.POLL_HOME_SERVER) {
						event = Event.WAIT;
						waitIntervalMillis = pollingIntervalMillis;
					}
				}
			}

			if (event == Event.PROCESS_DEVICE_UPDATES) {
				log(Level.FINE, "process device updates", null);
				homeServer.executeRemoteDeviceUpdates(deviceUpdateClient, log); // this may take many milliseconds and 'event' could change in the meantime
				synchronized (this) {
					if (event == Event.STOP) {
						break loop;
					}
					final long pollingCycleRemainingMillis = pollingIntervalMillis - (System.currentTimeMillis() - pollingCycleStartTime);
					if (pollingCycleRemainingMillis > 0) {
						event = Event.WAIT;
						waitIntervalMillis = pollingCycleRemainingMillis;
					} else {
						// poll immediately:
						event = Event.POLL_HOME_SERVER;
					}
				}
			}

			synchronized (this) {
				if (event == Event.WAIT) {
					log(Level.FINE, "wait " + waitIntervalMillis + " ms", null);
					event = Event.POLL_HOME_SERVER; // default action after wait (may be changed during wait period)

					wait(waitIntervalMillis); // "sleep"

					if (event == Event.STOP) {
						break loop;
					}
				}
			}
		}
	}

	/**
	 * <em>Note: </em> this method is not executed within a {@code synchronized} block.
	 */
	private void pollHomeServer() {
		boolean shouldStop = false;
		try {
			homeServer.updateLastHomeServerPollTime();

			final HomeServerResponse response = publicClient.getRegisteredDevices();

			final List<Device> devices = response.devices;
			if (response == null || devices == null) {
				throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR);
			}
			if (response.success) {
				setState(State.OK);
				pollingFailureCount = 0;
				try {
					final List<String> devicesNeedingStatus = homeServer.updateDeviceControllers(devices);
					if (devicesNeedingStatus != null) { // some devices need the Status block for the device => poll again
						for (String deviceID : devicesNeedingStatus) {

							final HomeServerResponse deviceResponse = publicClient.getDeviceStatus(deviceID);

							assert !deviceResponse.devices.isEmpty();
							final Status status = deviceResponse.devices.get(0).status;
							if (status == null) {
								// this is not an assert statement because failure to check this condition (i.e. asserts not checked) would result in
								// permanently missing information for a critical scheduler decision as the device update would re-request the Status block
								throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR, "Status block missing", null);
							}
							updateDeviceEntry(devices, deviceID, status);
						}
						@SuppressWarnings("unused")
						final List<String> ignored = homeServer.updateDeviceControllers(devices);
					}
					return;

				} catch (UnsupportedModelException ume) {
					throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR, null, ume);
				}
			}
			throw new ClientException(ClientException.Error.APPLICATION_FAILURE_RESPONSE);

		} catch (ClientException e) {
			if (e.getCause() == null) { // it's an application problem not a communication problem
				log(Level.SEVERE, "Exception in event loop", e);
			}
			lastClientException = e;
			switch (e.getError()) {
			case INTERRUPTED:
				shouldStop = true;
				break; // => exit
			case APPLICATION_DATA_ERROR:
			case EXECUTION_ERROR:
				setState(State.ERROR);
				// no recovery expected => exit
				shouldStop = true;
				break;
			case APPLICATION_FAILURE_RESPONSE:
				setState(State.ERROR);
				// we may recover on the next attempt
				// TODO further analysis of the actual return status
				break;
			case APPLICATION_TIMEOUT:
			case NETWORK_TIMEOUT:
				setState(State.TIMEOUT);
				// we may recover on the next attempt
				break;
			default:
				throw new IllegalArgumentException(e.getError().toString());
			}

			// Retry mechanism:
			pollingFailureCount++;
			if (pollingFailureCount > 5) {
				shouldStop = true; // give up => exit
			}

			if (shouldStop) {
				stop();
			}
		}
	}

	/**
	 * Replace the {@link Info} block in the list entry with {@code deviceID} by the given {@link Status} block.
	 * 
	 * @param devices
	 * @param deviceID
	 * @param status
	 */
	private void updateDeviceEntry(final List<Device> devices, String deviceID, final Status status) {
		assert deviceID != null;
		assert status != null;
		for (Device device : devices) {
			if (deviceID.equals(device.id)) {
				device.status = status;// set Status block
				// ensure errors, flags, etc. in device.status and device.info are never out-of-synch:
				device.info = null;
				return;
			}
		}
		throw new IllegalStateException("Device no longer in list: " + deviceID);
	}

	@Override
	public void devicesControllersUpdated(HomeServer server, boolean urgent) {
		// ignore

	}

	@Override
	public synchronized void deviceUpdatesPending(HomeServer server, boolean urgent) {
		if (runner != null && event != Event.STOP) {
			event = Event.PROCESS_DEVICE_UPDATES;
			this.notify(); // ends the "run()" loop
		}
	}

	public ClientException getLastClientException() {
		return lastClientException;
	}

	private void log(Level level, String message, Throwable ex) {
		log.log(level, homeServer.getUri().toString() + ": " + message, ex);
	}

}
