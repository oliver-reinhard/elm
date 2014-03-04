package elm.scheduler.model;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.hs.api.model.ElmStatus;
import elm.hs.api.model.Info;
import elm.hs.api.model.Status;
import elm.scheduler.HomeServerController;
import elm.scheduler.ElmScheduler;

/**
 * A {@link DeviceController} manages one physical {@link Device} and is the devices interface for the scheduler. Despite its name the {@link DeviceController}
 * is a re-active object rather than an object with its own {@link Thread}. It reacts to events from the {@link HomeServerController} and from the
 * {@link ElmScheduler}.
 * <p>
 * {@link DeviceController} has the following responsibilities:
 * <ul>
 * <li>reflect the true state of the underlying physical {@link Device}</li>
 * <li>track consumptions and consumption state</li>
 * <li>map power [W] to scald-protection temperature</li>
 * <li>act as an asynchronous communication adapter between the scheduler and the actual device</li>
 * </ul>
 * /p>
 */
public interface DeviceController {

	public static final int NO_POWER = 0;
	public static final int UNLIMITED_POWER = Integer.MAX_VALUE;

	public static final long NO_CONSUMPTION = -1;

	public static final short UNDEFINED_TEMPERATURE = -1;

	public enum DeviceStatus {

		/** Initial status. */
		INITIALIZING(false, false),
		/** Device has been registered at the home server but there is currently no connection to it. */
		NOT_CONNECTED(false, false),
		/** Device is ready for hot-water consumption without power limit. */
		READY(false, false),
		/** Device has recently started a hot-water consumption that has not been approved by the scheduler yet. */
		CONSUMPTION_STARTED(true, true),
		/** Device is currently consuming hot water after approval by the scheduler; the power consumption will not be interrupted. */
		CONSUMPTION_APPROVED(true, false),
		/**
		 * Device is currently consuming hot water and the scheduler has approved, however, with a power limit lower than requested by the user; the power
		 * consumption will not be interrupted.
		 */
		CONSUMPTION_LIMITED(true, false),
		/** Consumer has started a consumption but was denied the power consumption by the scheduler; the heater is off and cold water runs. */
		CONSUMPTION_DENIED(true, false),
		/** Device has ended started a hot-water consumption that has not been approved by the scheduler yet. */
		CONSUMPTION_ENDED(false, true),
		/** Device is in a technical error condition. */
		ERROR(false, false);

		private final boolean consuming;
		private final boolean transitioning;

		private DeviceStatus(boolean consuming, boolean transitioning) {
			this.consuming = consuming;
			this.transitioning = transitioning;
		}

		public boolean in(DeviceStatus... other) {
			for (DeviceStatus value : other) {
				if (this == value) return true;
			}
			return false;
		}

		public boolean isConsuming() {
			return consuming;
		}

		public boolean isTransitioning() {
			return transitioning;
		}
	}

	public enum UpdateResult {
		NO_UPDATES,
		/** No immediate scheduler pass is required, updates can be propagated on the next opportunity. */
		MINOR_UPDATES,
		/** Immediate scheduler pass is required. */
		URGENT_UPDATES,
		/** The device update was based on device {@link Info} only, device {@link Status} is required immediately. */
		DEVICE_STATUS_REQUIRED,
		/** Update information inconsistent or erroneous. */
		ERROR;

		public UpdateResult and(UpdateResult result) {
			assert result != null;
			if (this == ERROR || result == ERROR) {
				return ERROR;
			}
			if (this == DEVICE_STATUS_REQUIRED || result == DEVICE_STATUS_REQUIRED) {
				return DEVICE_STATUS_REQUIRED;
			} else if (this == URGENT_UPDATES || result == URGENT_UPDATES) {
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
	 * A user-assigned name or the {@link #getId() id} of the underlying physical {@link Device} if no explicit name is assigned.
	 * 
	 * @return never {@code null}
	 */
	String getName();

	/**
	 * @return never {@code null}
	 */
	HomeServer getHomeServer();

	/**
	 * @return never {@code null}
	 */
	DeviceModel getDeviceModel();

	/**
	 * @return never {@code null}
	 */
	DeviceStatus getStatus();

	/**
	 * Updates the internal state and values from the given {@link Device}.
	 * 
	 * @param device
	 *            cannot be {@code null}
	 * @return never {@code null}
	 */
	UpdateResult update(Device device);

	/**
	 * Invoked only by the scheduler. This method may be invoked at any time even with the same value for {@code approvedPowerWatt}, i.e. implementations must
	 * guard against this case to avoid computing overhead.
	 * <p>
	 * <em>Note: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param elmStatus
	 *            the current {@link ElmStatus}, cannot be {@code null}
	 * @param approvedPowerWatt
	 *            the power (in [W] the device may consume)
	 * 
	 */
	void updateMaximumPowerConsumption(ElmStatus elmStatus, int approvedPowerWatt);

	/**
	 * Invoked only by the scheduler on scheduler status changes.
	 * <p>
	 * <em>Note: </em>This method is always called by the scheduler after {@link #updateMaximumPowerConsumption(ElmStatus, int)}.
	 * </p>
	 * <p>
	 * <em>Note 2: </em>This method must not be long-running or blocking; this could delay the scheduler.
	 * </p>
	 * 
	 * @param elmStatus
	 *            the current {@link ElmStatus}, cannot be {@code null}
	 * @param expectedWaitingTimeMillis
	 *            expected waiting time for this device if ELM status is {@code OVERLOAD}, in [ms]
	 */
	void updateUserFeedback(ElmStatus newStatus, int expectedWaitingTimeMillis);

	/**
	 * The time the current consumption started, or {@link #NO_CONSUMPTION} if the device does not currently provide hot water.
	 * 
	 * @return Unix time in milliseconds
	 */
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
	 * The mean duration this device is being used.
	 */
	int getMeanConsumptionMillis();

}
