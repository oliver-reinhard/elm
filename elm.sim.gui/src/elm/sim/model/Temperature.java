package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum Temperature implements SimEnum {
	/** The minimum temperature is given by the temperature of the inflowing cold water. */
	TEMP_MIN(15), TEMP_1(30), TEMP_2(38), TEMP_3(42), TEMP_4(48),
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

	public boolean lessThan(Temperature other) {
		return this.getDegreesCelsius() < other.getDegreesCelsius();
	}

	public boolean lessOrEqualThan(Temperature other) {
		return this.getDegreesCelsius() <= other.getDegreesCelsius();
	}

	public static Temperature min(Temperature a, Temperature b) {
		assert a != null;
		assert b != null;
		return (a.lessThan(b)) ? a : b;
	}

	public static Temperature min(Temperature a, Temperature b, Temperature c) {
		return min(min(a,b),c);
	}

	public static Temperature fromInt(int degreesCelsius) {
		assert degreesCelsius >= TEMP_MIN.degreesCelsius && degreesCelsius <= TEMP_MAX.degreesCelsius;
		Temperature prev = TEMP_MIN;
		for (Temperature t : values()) {
			int average = (prev.degreesCelsius + t.degreesCelsius) / 2;
			if (degreesCelsius <= average) {
				return prev;
			} else if (degreesCelsius <= t.degreesCelsius) {
				return t;
			}
			prev = t;
		}
		throw new IllegalStateException();
	}
}
