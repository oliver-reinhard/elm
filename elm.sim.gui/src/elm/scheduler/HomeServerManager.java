package elm.scheduler;

import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import elm.hs.api.client.ClientException;
import elm.hs.api.client.ClientUtil;
import elm.hs.api.client.HomeServerInternalApiClient;
import elm.hs.api.client.HomeServerPublicApiClient;
import elm.hs.api.model.HomeServerResponse;
import elm.scheduler.model.HomeServer;

public class HomeServerManager implements Runnable, HomeServerChangeListener {

	private static final int POLLING_INTERVAL_MILLIS = 1000;
	private static final int EXCENDED_POLLING_INTERVAL_MILLIS = 5000;

	public enum State {
		NOT_CONNECTED, OK, TIMEOUT, STOPPED, ERROR
	}

	private enum Event {
		WAIT, POLL_HOME_SERVER, PROCESS_DEVICE_UPDATES, STOP
	}

	private final HomeServer homeServer;
	private State state = State.NOT_CONNECTED;

	private HomeServerPublicApiClient publicClient = null;
	private HomeServerInternalApiClient internalClient = null;
	private ClientException lastClientException = null;
	private Event event = Event.POLL_HOME_SERVER;
	private Thread runner;

	private int pollingFailureCount = 0;
	private int pollingIntervalMillis = POLLING_INTERVAL_MILLIS;
	private final Logger log = Logger.getLogger(getClass().getName());

	public HomeServerManager(HomeServer homeServer) {
		assert homeServer != null;
		this.homeServer = homeServer;
		this.homeServer.addChangeListener(this);
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

		internalClient = new HomeServerInternalApiClient(homeServer.getPassword(), publicClient);
		// ClientUtil.initSslContextFactory(internalClient.getClient());

		setState(State.OK);
		runner = new Thread(this, HomeServerManager.class.getSimpleName());
		event = Event.STOP;
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
				// internalClient.start();
			} catch (Exception e) {
				log(Level.SEVERE, "Cannot start HTTP client", e);
				setState(State.ERROR);
				return;
			}

			processEvents(); // throws InterruptedException

		} catch (InterruptedException e) {
			// do nothing, we have already exited the event loop
		} finally {
			runner = null;
			setState(State.STOPPED);
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

	private void processEvents() throws InterruptedException {
		long waitIntervalMillis = pollingIntervalMillis;
		long pollingCycleStartTime = System.currentTimeMillis();

		eventLoop: while (true) {

			if (event == Event.POLL_HOME_SERVER) {
				// this may take many milliseconds:
				pollHomeServer();
				pollingCycleStartTime = System.currentTimeMillis();
				synchronized (this) {
					if (event == Event.STOP) {
						break eventLoop;
					}
					if (event == Event.POLL_HOME_SERVER) {
						event = Event.WAIT;
						waitIntervalMillis = pollingIntervalMillis;
					}
				}
			}
			
			if (event == Event.PROCESS_DEVICE_UPDATES) {
				// this may take many milliseconds:
				homeServer.executeDeviceUpdates(internalClient);
				synchronized (this) {
					if (event == Event.STOP) {
						break eventLoop;
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

					wait(waitIntervalMillis); // "sleep"

					if (event == Event.STOP) {
						break eventLoop;
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
			final HomeServerResponse response = publicClient.getAllDevices();
			pollingIntervalMillis = POLLING_INTERVAL_MILLIS;
			if (response == null || response.devices == null) {
				throw new ClientException(ClientException.Error.APPLICATION_DATA_ERROR);
			}
			if (response.success) {
				setState(State.OK);
				pollingFailureCount = 0;
				homeServer.updateDeviceInfos(response.devices);
			}
			throw new ClientException(ClientException.Error.APPLICATION_FAILURE_RESPONSE);

		} catch (ClientException e) {
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
			if (pollingFailureCount == 3) {
				pollingFailureCount = EXCENDED_POLLING_INTERVAL_MILLIS;
			} else if (pollingFailureCount > 5) {
				shouldStop = true; // give up => exit
			}

			if (shouldStop) {
				stop();
			}
		}
	}

	@Override
	public void deviceInfosUpdated(HomeServer server, boolean urgent) {
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
