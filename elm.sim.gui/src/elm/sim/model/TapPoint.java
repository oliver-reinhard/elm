package elm.sim.model;

import elm.hs.api.model.ElmStatus;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.Scheduler;
import elm.scheduler.model.DeviceController;
import elm.sim.metamodel.SimAttribute;
import elm.sim.metamodel.SimChangeNotifier;
import elm.sim.ui.SimpleSchedulerUI;

public interface TapPoint extends SimChangeNotifier {

	/** A simple metamodel of the {@link TapPoint}. */
	public enum Attribute implements SimAttribute {

		NAME("Name"),
		ID("ID"),
		REFERENCE_FLOW("Soll-Menge"),
		ACTUAL_FLOW("Ist-Menge"),
		REFERENCE_TEMPERATURE("Soll-Temperatur"),
		ACTUAL_TEMPERATURE("Ist-Temperatur"),
		SCALD_TEMPERATURE("Verbrühschutztemperatur"),
		STATUS("Status"),
		WAITING_TIME_PERCENT("Wartezeit [%]"),
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

	public static final int NO_WAITING_PERCENT = 0;
	public static final int MAX_WAITING_PERCENT = 100;

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

	void setReferenceTemperature(HotWaterTemperature newValue);

	HotWaterTemperature getReferenceTemperature();

	void setScaldProtectionTemperature(HotWaterTemperature newValue);

	HotWaterTemperature getScaldProtectionTemperature();

	HotWaterTemperature getActualTemperature();

	SimStatus getStatus();

	/**
	 * This method is used by the real ELM {@link Scheduler}.
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

	void setWaitingTimePercent(int newValue);

	int getWaitingTimePercent();

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

}