package elm.scheduler.model;

import elm.hs.api.model.Device;

public interface DeviceInfo {

	public static final int NO_POWER = 0;
	public static final int UNLIMITED_POWER = -1;

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
		NO_UPDATES, MINOR_UPDATES, URGENT_UPDATES, ERROR;

		public UpdateResult and(UpdateResult result) {
			assert result != null;
			if (this == ERROR || result == ERROR) {
				return ERROR;
			}
			if (this == URGENT_UPDATES || result == URGENT_UPDATES) {
				return URGENT_UPDATES;
			} else if (this == MINOR_UPDATES || result == MINOR_UPDATES) {
				return MINOR_UPDATES;
			}
			return NO_UPDATES;
		}
	}

	/**
	 * The id of the underlying physical {@link Device}.
	 * 
	 * @return never {@code null}
	 */
	String getId();

	/**
	 * A user-assigned name or the id of the underlying physical {@link Device} if name is {@code null}.
	 * 
	 * @return never {@code null}
	 */
	String getName();

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

	/**
	 * The power in [W] the device should consume to satisfy the user's choice of temperature and flow.
	 */
	int getDemandPowerWatt();

	/**
	 * The maximum power [W] the device may consume as granted by the scheduler.
	 */
	int getApprovedPowerWatt();

	/**
	 * The scald temperature limit that corresponds to the current {@link #getApprovedPowerWatt()}.
	 * 
	 * @return value in [° Celsius]
	 */
	int getScaldTemperature();

	/**
	 * Invoked only by the scheduler.
	 * 
	 * @param actualPower
	 *            the power (in [W] the device may consume)
	 */
	void powerConsumptionApproved(int actualPowerWatt);

	/**
	 * Invoked only by the scheduler.
	 */
	void powerConsumptionDenied();

}
