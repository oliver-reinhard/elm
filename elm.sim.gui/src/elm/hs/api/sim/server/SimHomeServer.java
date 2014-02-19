package elm.hs.api.sim.server;

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

	void setIntakeWaterTemperature(IntakeWaterTemperature newValue);

	IntakeWaterTemperature getIntakeWaterTemperature();
}
