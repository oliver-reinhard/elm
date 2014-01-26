package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum Temperature implements SimEnum {
	/** The minimum temperature is given by the temperature of the inflowing cold water. */
	TEMP_MIN(0),
	TEMP_1(30), TEMP_2(38), TEMP_3(42), TEMP_4(48),
	/** The maxium temperature is given by the technical upper temperature limit of the water heater. */
	TEMP_MAX(60);

	private final int degreesCelsius;

	Temperature(int degreesCelsius) {
		this.degreesCelsius = degreesCelsius;
	}

	public int getDegreesCelsius() {
		return degreesCelsius;
	}

	public String getLabel() {
		return this == TEMP_MIN ? "Kalt" : (Integer.toString(degreesCelsius) + "°C");
	}
	
	public boolean greaterThan(Temperature other) {
		return this.getDegreesCelsius() > other.getDegreesCelsius();
	}
	
	public boolean lessThan(Temperature other) {
		return this.getDegreesCelsius() < other.getDegreesCelsius();
	}
}
