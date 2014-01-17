package elm.sim.model;

public enum Temperature {
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
}
