package elm.sim.ui;

import java.net.UnknownHostException;

import elm.hs.api.sim.server.SimHomeServer;
import elm.scheduler.model.UnsupportedModelException;
import elm.sim.model.SimpleScheduler;
import elm.sim.model.TapPoint;

public abstract class AbstractSimServerApplicationConfiguration {

	/**
	 * An x-y-grid of tap points. Layout will be according to rows ({@code y}, first index) and their elements ({@code x}, second index).
	 * <p>
	 * Cannot be {@code null}, individual grid elements cannot be {@code null}.
	 */
	protected TapPoint[][] tapPoints;

	/** Cannot be {@code null} */
	protected SimHomeServer server;

	/** Optional, can be {@code null} */
	protected SimpleScheduler scheduler;

	/**
	 * Populates the fields.
	 * 
	 * @param createSimpleScheduler
	 *            if {@code true} then create a scheduler
	 * @param serverPort
	 *            IP port for the server
	 * @throws UnsupportedModelException
	 *             if one of the tap points' id does not map to a known device model.
	 * @throws UnknownHostException 
	 */
	public abstract void init(final boolean createSimpleScheduler, int serverPort) throws UnsupportedModelException, UnknownHostException;

	public TapPoint[][] getTapPoints() {
		return tapPoints;
	}

	/**
	 * Returns a server object that is populated with devices and adapters for {@link #getPoints() tap points}.
	 * 
	 * @return never null {@code null}
	 */
	public SimHomeServer getServer() {
		return server;
	}

	/**
	 * Optional.
	 * 
	 * @return can be {@code null}
	 */
	public SimpleScheduler getScheduler() {
		return scheduler;
	}

}
