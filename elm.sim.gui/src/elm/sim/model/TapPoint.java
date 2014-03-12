package elm.sim.model;

import elm.hs.api.ElmStatus;
import elm.hs.api.Status;
import elm.hs.api.DeviceCharacteristics.DeviceModel;
import elm.scheduler.ElmScheduler;
import elm.scheduler.model.DeviceController;
import elm.sim.metamodel.SimAttribute;
import elm.sim.metamodel.SimChangeNotifier;
import elm.sim.ui.SimpleSchedulerUI;

public interface TapPoint extends SimChangeNotifier {

	static final long NO_CONSUMPTION = -1;

	/** A simple metamodel of the {@link TapPoint}. */
	public enum Attribute implements SimAttribute {

		NAME("Name"),
		ID("ID"),
		REFERENCE_FLOW("Soll-Menge"),
		ACTUAL_FLOW("Ist-Menge"),
		CONSUMPTION_START_TIME("Beginn"),
		REFERENCE_TEMPERATURE("Soll-Temperatur"),
		ACTUAL_TEMPERATURE("Ist-Temperatur"),
		SCALD_PROTECTION_TEMPERATURE("Verbrühschutztemperatur"),
		STATUS("Status"),
		WAITING_TIME_MILLIS("Wartezeit"),
		INTAKE_WATER_TEMPERATURE("Kaltwassertemp.");

		private final String label;

		private Attribute(String label) {
			this.label = label;
		}

		public String id() {
			return "OUTLET__" + name();
		}

		@Override
		public String getLabel() {
			return label;
		}
	}

	/**
	 * @return never {@code null} or empty
	 */
	String getName();

	String getId();

	boolean isSimDevice();

	DeviceModel getDeviceModel();

	String getLabel();

	SimAttribute[] getSimAttributes();

	void setReferenceFlow(Flow newValue);

	Flow getReferenceFlow();

	Flow getActualFlow();

	/**
	 * Returns the time the {@link #getActualFlow() actual flow} went from {@code 0} to a value greatr than {@code 0}.
	 * 
	 * @return {@link #NO_CONSUMPTION} if the {@link #getActualFlow() actual flow} is currently {@code 0}.
	 */
	long getConsumptionStartTimeMillis();

	void setReferenceTemperature(HotWaterTemperature newValue);

	HotWaterTemperature getReferenceTemperature();

	void setScaldProtectionTemperature(HotWaterTemperature newValue);

	HotWaterTemperature getScaldProtectionTemperature();

	/** The actual temperature is never lower than the {@link #getIntakeWaterTemperature() intake water temperature}. */
	HotWaterTemperature getActualTemperature();

	SimStatus getStatus();

	/**
	 * This method is used by the real ELM {@link ElmScheduler}.
	 * 
	 * @param deviceStatus
	 *            the actual ELM {@link DeviceController}'s status, cannot be {@code null}
	 */
	void setStatus(ElmStatus deviceStatus);

	/**
	 * This method is ONLY used for tests or demos with the {@link SimpleScheduler} and {@link SimpleSchedulerUI}. It derives the
	 * {@link #getScaldProtectionTemperature() scald-protection temperature} and the {@link #getActualTemperature() actual temperature} mainly from the
	 * scheduler status.
	 * 
	 * @param schedulerStatus
	 *            cannot be {@code null}
	 */
	void setSchedulerStatus(SimStatus schedulerStatus);

	void setWaitingTimeMillis(int newValue);

	int getWaitingTimeMillis();

	void setIntakeWaterTemperature(IntakeWaterTemperature newValue);

	IntakeWaterTemperature getIntakeWaterTemperature();

	/**
	 * {@link #setId(String)} must have been called before invoking this method.
	 * 
	 * @return the currently consumed power [W]
	 */
	int getPowerWatt();

	/**
	 * {@link #setId(String)} must have been called before invoking this method.
	 * 
	 * @return the currently consumed power in device units in relation to {@link DeviceModel#getPowerMaxUnits()} configuration.
	 */
	short getPowerUnits();

	/**
	 * @return the {@link Status#flags} for this device
	 */
	short getFlags();

}