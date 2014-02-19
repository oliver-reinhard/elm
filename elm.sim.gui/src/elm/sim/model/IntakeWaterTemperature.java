package elm.sim.model;

import elm.sim.metamodel.SimEnum;

public enum IntakeWaterTemperature implements SimEnum {

	/** HomeServer temperature units in [1/10°C] */
	TEMP_5(50), TEMP_10(100), TEMP_15(150), TEMP_20(200);

	private final short units;

	private IntakeWaterTemperature(int units) {
		this.units = (short) units;
	}

	public int getDegreesCelsius() {
		return units / 10;
	}

	/** HomeServer temperature units in [1/10°C] */
	public short getUnits() {
		return units;
	}

	public String getLabel() {
		return (Integer.toString(getDegreesCelsius()) + "°C");
	}

	/** HomeServer temperature units in [1/10°C] */
	public static IntakeWaterTemperature fromShort(short units) {
		assert units >= TEMP_5.units && units <= TEMP_20.units;
		IntakeWaterTemperature prev = TEMP_5;
		for (IntakeWaterTemperature t : values()) {
			int average = (prev.units + t.units) / 2;
			if (units <= average) {
				return prev;
			} else if (units <= t.units) {
				return t;
			}
			prev = t;
		}
		throw new IllegalStateException();
	}
}
