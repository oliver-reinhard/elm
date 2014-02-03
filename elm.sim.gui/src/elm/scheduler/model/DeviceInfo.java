package elm.scheduler.model;

import elm.hs.api.model.Device;

public interface DeviceInfo {

	public static final int NO_POWER_LIMIT = -1;

	public enum State {
		/** Device has been registered at the home server but there is currently no connection to it. */
		NOT_CONNECTED,
		/** Device is ready for hot-water consumption. */
		READY,
		/** Device is currently consuming hot water. */
		CONSUMING,
		/** Consumer has started a consumption but the heater is off waiting for power quota. */
		WAITING,
		/** Device is in a technical error condition. */
		ERROR
	}
	
	public enum UpdateResult {
		NO_UPDATES, MINOR_UPDATES, URGENT_UPDATES;
		
		public UpdateResult and(UpdateResult result) {
			assert result != null;
			if(this == URGENT_UPDATES || result == URGENT_UPDATES) {
				return URGENT_UPDATES;
			} else if (this == MINOR_UPDATES || result == MINOR_UPDATES) {
				return MINOR_UPDATES;
			} return NO_UPDATES;
		}
	}

	String getId();

	HomeServer getHomeServer();

	State getState();

	/**
	 * Updates the internal state and values from the given {@link Device}.
	 * 
	 * @param device
	 *            cannot be {@code null}
	 * @return never {@code null}
	 */
	UpdateResult update(Device device);

	long getConsumptionStartTime();

	int getDemandPowerWatt();

	int getActualPowerWatt();

	void setActualPowerWatt(int actualPower);

	/**
	 * Invoked by the actual physical device.
	 * 
	 * @param demandPower
	 *            the power needed to satisfy the user demand (temperature, flow).
	 */
	void waterConsumptionStarted(int demandPower);

	/**
	 * Invoked by the actual physical device.
	 */
	void waterConsumptionEnded();

	/**
	 * Invoked by the scheduler.
	 * 
	 * @param actualPower
	 *            the power (in [W] the device may consume)
	 */
	void powerConsumptionApproved(int actualPower);

	/**
	 * Invoked by the scheduler.
	 */
	void powerConsumptionDenied();

}
