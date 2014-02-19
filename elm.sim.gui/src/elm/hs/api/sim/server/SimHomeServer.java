package elm.hs.api.sim.server;

import java.net.URI;
import java.util.Collection;

import elm.hs.api.model.Device;
import elm.hs.api.model.HomeServerResponse;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.IntakeWaterTemperature;
import elm.ui.api.ElmUserFeedback;

public interface SimHomeServer {
	
	/** A simple metamodel of the {@link SimHomeServer}. */
	public enum Attribute implements SimAttribute {
		INTAKE_WATER_TEMPERATURE("Kaltwassertemperatur");

		private final String label;

		private Attribute(String label) {
			this.label = label;
		}

		public String id() {
			return name();
		}

		@Override
		public String getLabel() {
			return label;
		}
	}


	URI getUri();

	Collection<Device> getDevices();

	void setIntakeWaterTemperature(IntakeWaterTemperature newValue);

	IntakeWaterTemperature getIntakeWaterTemperature();


	/**
	 * Gerneral Http {@code /} request.
	 * 
	 * @return never {@code null}
	 */
	HomeServerResponse processStatusRequest();

	/**
	 * Http {@code /devices} request.
	 * 
	 * @return never {@code null}
	 */
	HomeServerResponse processDevicesRequest();

	/**
	 * Http {@code /devices/status/<id>} request.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @return {@code null} if no device with the given id exists
	 */
	HomeServerResponse processDeviceStatusRequest(String id);

	/**
	 * Http {@code /devices/setpoint/<id>} with a body of {@code data=<temperature>} request. Changes the setpoint of the given device in the database and
	 * returns the proper response.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @param setpoint
	 *            reference temperature in [1/10°C]
	 * @return {@code null} if no device with the given id exists
	 */
	HomeServerResponse processDeviceSetpoint(String id, short setpoint);

	/**
	 * Http {@code /devices/feedback} with a body of {@link ElmUserFeedback} request.
	 * 
	 * @param feedback
	 *            cannot be {@code null}
	 */
	void processUserFeedback(ElmUserFeedback feedback);

	/**
	 * Http {@code /cmd/VF/} with a body of {@code data=<temperature>} request. Changes the scald-protection temperature.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @return {@code null} if no device with the given id exists
	 */
	HomeServerResponse processSetScaldProtectionFlag(String id, boolean on);

	/**
	 * Http {@code /cmd/Vv/} with a body of {@code data=<temperature>} request. Changes the scald-protection temperature.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @param temperature
	 *            [1/10°C] for scald protection
	 * @return {@code null} if no device with the given id exists
	 */
	HomeServerResponse processSetScaldProtectionTemperature(String id, short temperature);
}
