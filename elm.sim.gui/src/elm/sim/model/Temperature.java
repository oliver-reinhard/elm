package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum Temperature implements SimEnum {
	TEMP_1(30), TEMP_2(38), TEMP_3(42), TEMP_4(48);

	private final int degreesCelsius;

	Temperature(int degreesCelsius) {
		this.degreesCelsius = degreesCelsius;
	}

	public int getDegreesCelsius() {
		return degreesCelsius;
	}

	public String getLabel() {
		return Integer.toString(degreesCelsius) + "°C";
	}
	
	public boolean greaterThan(Temperature other) {
		return this.getDegreesCelsius() > other.getDegreesCelsius();
	}
	
	public boolean lessThan(Temperature other) {
		return this.getDegreesCelsius() < other.getDegreesCelsius();
	}
}
