package elm.hs.api.sim.server;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import elm.hs.api.model.Device;
import elm.hs.api.model.ElmUserFeedback;
import elm.hs.api.model.HomeServerResponse;
import elm.sim.metamodel.SimAttribute;
import elm.sim.model.IntakeWaterTemperature;

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
	 * Gerneral  GET Http {@code ""} request.
	 * 
	 * @return never {@code null}
	 */
	HomeServerResponse processStatusQuery();

	/**
	 * Http GET {@code /devices} request.
	 * 
	 * @return never {@code null}
	 */
	HomeServerResponse processDevicesQuery();

	/**
	 * Http GET {@code /devices/status/<id>} request.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @return {@code null} if no device with the given id exists
	 */
	HomeServerResponse processDeviceStatusQuery(String id);

	/**
	 * Http POST {@code /devices/setpoint/<id>} with a body of {@code data=<temperature>} request. Changes the setpoint of the given device in the database and
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
	 * Http POST {@code /cmd/VF/} with a body of {@code data=<temperature>} request. Changes the scald-protection temperature.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @return {@code null} if no device with the given id exists
	 */
	HomeServerResponse processSetScaldProtectionFlag(String id, boolean on);

	/**
	 * Http POST {@code /cmd/Vv/} with a body of {@code data=<temperature>} request. Changes the scald-protection temperature.
	 * 
	 * @param id
	 *            cannot be {@code null} or empty
	 * @param temperature
	 *            [1/10°C] for scald protection
	 * @return {@code null} if no device with the given id exists
	 */
	HomeServerResponse processSetScaldProtectionTemperature(String id, short temperature);

	/**
	 * Http GET {@code /devices/feedback} without parameter or request body. This returns a list of device IDs whose feedback is handled by this server.
	 */
	HomeServerResponse processDevicesFeedbackQuery();

	/**
	 * Http POST {@code /devices/feedback} with a body of List of {@link ElmUserFeedback} request.
	 * 
	 * @param feedback
	 *            cannot be {@code null}
	 */
	void processUserFeedback(List<ElmUserFeedback> feedback);
}
