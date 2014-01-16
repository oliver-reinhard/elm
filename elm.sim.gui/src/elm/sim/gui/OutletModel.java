package elm.sim.gui;

/**
 * Apart from the name, which is mandatory, all fields are optional.
 */
public class OutletModel {

	/** A simple metamodel of the {@link OutletModel}. */
	public enum Properties {
		NAME, FLOW, TEMPERATURE, ON;
		
		public String id() {
			return "OUTLET__" + name();
		}
	}

	private final String name;
	private Flow flow;
	private Temperature temperature;
	private Boolean on;

	public OutletModel(String name, Flow flow) {
		this(name, flow, null, null);
	}

	public OutletModel(String name, Temperature temperature) {
		this(name, null, temperature, null);
	}

	public OutletModel(String name, Boolean on) {
		this(name, null, null, on);
	}

	public OutletModel(String name, Flow menge, Temperature temperature, Boolean on) {
		assert name != null && !name.isEmpty();
		this.name = name;
		this.flow = menge;
		this.temperature = temperature;
		this.on = on;
	}

	/**
	 * @return never {@code null} or empty
	 */
	public String getName() {
		return name;
	}

	public Flow getFlow() {
		return flow;
	}

	public Temperature getTemperature() {
		return temperature;
	}

	public Boolean isOn() {
		return on;
	}
}
