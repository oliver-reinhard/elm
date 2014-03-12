package elm.hs.api;

public class DeviceCharacteristics {

	public enum DeviceType {

		TYPE_0(false), TYPE_1(true), TYPE_2(false), TYPE_3(true), TYPE_4(false), TYPE_5(true),
		/** Simulated. */
		TYPE_7_SIM(true);

		final boolean remoteControllable;

		private DeviceType(boolean remoteControllable) {
			this.remoteControllable = remoteControllable;
		}

		public int getDeviceClassId() {
			return ordinal();
		}

		public boolean isRemoteControllable() {
			return remoteControllable;
		}

		public static DeviceType fromId(int deviceClassId) {
			for (DeviceType type : values()) {
				if (type.ordinal() == deviceClassId) return type;
			}
			throw new IllegalArgumentException(Integer.toString(deviceClassId));
		}
	}

	public enum DeviceModel {
		/* No remote controllable => not suitable for ELM. */
		DBX(DeviceType.TYPE_0, 0), CDX(DeviceType.TYPE_2, 0), MBX(DeviceType.TYPE_4, 0),

		/* Remote controllable */
		DCX(DeviceType.TYPE_1, 27_000), DEX(DeviceType.TYPE_1, 27_000), DSX(DeviceType.TYPE_1, 27_000), CEX(DeviceType.TYPE_3, 13_500), CFX(DeviceType.TYPE_3,
				13_500), MCX3(DeviceType.TYPE_5, 3_500), MCX4(DeviceType.TYPE_5, 4_400), MCX6(DeviceType.TYPE_5, 5_700), MCX7(DeviceType.TYPE_5, 6_500),
		/** Simulated. */
		SIM(DeviceType.TYPE_7_SIM, 22_000);

		final DeviceType type;
		final int powerMaxWatt;
		final short powerMaxUnits;
		final short temperatureOffUnits; // 1/10°C
		final short temperatureMaxUnits; // 1/10°C

		private DeviceModel(DeviceType type, int powerMaxWatt) {
			this(type, powerMaxWatt, 180, 190, 600);
		}

		private DeviceModel(DeviceType type, int powerMaxWatt, int powerMaxUnits, int temperatureOff, int temperatureMax) {
			this.type = type;
			this.powerMaxWatt = powerMaxWatt;
			this.powerMaxUnits = (short) powerMaxUnits;
			this.temperatureOffUnits = (short) temperatureOff;
			this.temperatureMaxUnits = (short) temperatureMax;
		}

		public DeviceType getType() {
			return type;
		}

		public int getPowerMaxWatt() {
			return powerMaxWatt;
		}

		public short getPowerMaxUnits() {
			return powerMaxUnits;
		}

		/**
		 * The device heater remains off for reference temperatures below this temperature, even if the intake water temperature is even lower.
		 * 
		 * @return temperature in [1/10°C].
		 */
		public short getTemperatureOff() {
			return temperatureOffUnits;
		}

		/** @return Maximum reference temperature in [1/10°C]. */
		public short getTemperatureMax() {
			return temperatureMaxUnits;
		}

		/**
		 * Returns the {@link DeviceModel} from the {@link Device#id}.
		 * 
		 * @param device
		 *            cannot be {@code null}
		 * @return {@code null} if device id does not reference a known device model
		 */
		public static DeviceModel getModel(Device device) {
			assert device != null;
			return getModel(device.id);
		}

		/**
		 * Returns the {@link DeviceModel} from the given {@link Device#id}.
		 * 
		 * @param deviceId
		 *            cannot be {@code null}
		 * @return {@code null} if device id does not reference a known device model
		 */
		public static DeviceModel getModel(String deviceId) {
			assert deviceId != null && deviceId.length() == 10;
			String typeIdStr = deviceId.substring(0, 4);
			int typeId = Integer.parseInt(typeIdStr, 16);
			int deviceClassId = typeId >> 13;
			@SuppressWarnings("unused")
			int variant = typeId & 0x1FFF;

			// TODO implement real mapping (deviceClassId, variant) => DeviceModel.

			DeviceType type = DeviceType.fromId(deviceClassId);
			switch (type) {
			/* Not remote controllable => not suitable for ELM. */
			case TYPE_0:
				return DeviceModel.DBX;
			case TYPE_2:
				return DeviceModel.CDX;
			case TYPE_4:
				return DeviceModel.MBX;

				/* Remote controllable */
			case TYPE_1:
				return DeviceModel.DSX;
			case TYPE_3:
				return DeviceModel.CEX;
			case TYPE_5:
				return DeviceModel.MCX3;
			case TYPE_7_SIM:
				return DeviceModel.SIM;
			default:
				throw new IllegalArgumentException(type.toString());
			}
		}
	}

}
