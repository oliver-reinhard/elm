package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum HotWaterTemperature implements SimEnum {
	/** The minimum temperature is a preset device characteristic. */
	TEMP_MIN_19(19), TEMP_25(25),
	TEMP_30(30), TEMP_38(38), TEMP_42(42), TEMP_48(48),
	/** The maximum temperature is given by the technical upper temperature limit of the water heater. */
	TEMP_MAX_60(60);

	private final int degreesCelsius;

	HotWaterTemperature(int degreesCelsius) {
		this.degreesCelsius = degreesCelsius;
	}

	public int getDegreesCelsius() {
		return degreesCelsius;
	}

	/** HomeServer temperature units in [1/10°C] */
	public short getUnits() {
		return (short) (degreesCelsius * 10);
	}

	public String getLabel() {
		return this == TEMP_MIN_19 ? "Kalt" : (Integer.toString(degreesCelsius) + "°C");
	}

	public boolean lessThan(HotWaterTemperature other) {
		return this.getDegreesCelsius() < other.getDegreesCelsius();
	}

	public boolean lessOrEqualThan(HotWaterTemperature other) {
		return this.getDegreesCelsius() <= other.getDegreesCelsius();
	}

	public static HotWaterTemperature min(HotWaterTemperature a, HotWaterTemperature b) {
		assert a != null;
		assert b != null;
		return (a.lessThan(b)) ? a : b;
	}

	public static HotWaterTemperature min(HotWaterTemperature a, HotWaterTemperature b, HotWaterTemperature c) {
		return min(min(a,b),c);
	}

	public static HotWaterTemperature fromInt(int degreesCelsius) {
		assert degreesCelsius >= TEMP_MIN_19.degreesCelsius && degreesCelsius <= TEMP_MAX_60.degreesCelsius;
		HotWaterTemperature prev = TEMP_MIN_19;
		for (HotWaterTemperature t : values()) {
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
