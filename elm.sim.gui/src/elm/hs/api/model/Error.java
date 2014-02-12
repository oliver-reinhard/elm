package elm.hs.api.model;

/** See Home Server API documentation for error numbers. */
public enum Error {

	OK(0, "kein Fehler"),
	
	// Home Server errors

	NOT_AVAILABLE(-1, "Gerät nicht angemeldet oder nicht (mehr) vorhanden"),

	HS_RES_1(-2, "reserviert"),

	TIMEOUT(-3, "Timeout, Gerät angemeldet aber antwortet nicht"),

	HS_RES_2(-4, "reserviert"),
	
	// Device errors

	BUS_ERROR(10, "Fehler Bussystem, Bedienfeld defekt?"),

	OVER_VOLTAGE_ERROR(11, "Überspannung"),

	UNDER_VOLTAGE_ERROR(12, "Unterspannung"),

	PHASE_ERROR(13, "Phasenfehler"),

	OUTTEMP_ERROR(51, "Auslauftemperatur falsch"),

	INTEMP_ERROR(53, "Einlauftemperatur falsch"),

	OUTTEMP_SENSOR_ERROR(56, "Temperaturfühler am Auslauf defekt"),

	INTEMP_SENSOR_ERROR(58, "Temperaturfühler am Einlauf defekt"),

	TEMP_SENSOR_SWITCHING_ERROR(59, "Temperaturfühler vertauscht"),

	CALIBRATION_HIGH_ERROR(61, "Kalibrierwert zu hoch"),

	CALIBRATION_LOW_ERROR(62, "Kalibrierwert zu niedrig"),

	HEATER_ERROR(63, "Fehler Heizelement"),

	INFLOW_ERROR(75, "Durchfluss zu groß (Luft im System)"),

	OUTFLOW_ERROR(76, "Auslauftemperatur zu groß (Luft im System)"),

	AIR_DETECTION_ERROR(77, "Luftblasen erkannt"),

	INIT_ERROR(80, "Initialisierungsfehler Funkmodul"),

	UNKNOWN_ERROR(99, "Unbekannter Fehler");

	private final short code;
	private final String description;

	private Error(int code, String description) {
		this.code = (short) code;
		this.description = description;
	}

	public short getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public boolean equals(short code) {
		return this.code == code;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(name());
		b.append("(");
		b.append(getCode());
		b.append(", \"");
		b.append(getDescription());
		b.append("\")");
		return b.toString();
	}
	
	public static Error fromCode(int code) {
		for(Error e : values()) {
			if (e.getCode() == code) {
				return e;
			}
		}
		throw new IllegalArgumentException("Unknown code: " + code);
	}

}
