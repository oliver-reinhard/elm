package elm.hs.api.model;

public class DeviceCharacteristics {

	public enum DeviceType {

		TYPE_0(false), TYPE_1(true), TYPE_2(false), TYPE_3(true), TYPE_4(false), TYPE_5(true);

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
				13_500), MCX3(DeviceType.TYPE_5, 3_500), MCX4(DeviceType.TYPE_5, 4_400), MCX6(DeviceType.TYPE_5, 5_700), MCX7(DeviceType.TYPE_5, 6_500);

		final DeviceType type;
		final int powerMaxWatt;
		final int scaldTemperatureMin; // degrees C

		final int scaldTemperatureMax; // degrees C

		private DeviceModel(DeviceType type, int powerMaxWatt, int scaldTemperatureMin, int scaldTemperatureMax) {
			this.type = type;
			this.powerMaxWatt = powerMaxWatt;
			this.scaldTemperatureMin = scaldTemperatureMin;
			this.scaldTemperatureMax = scaldTemperatureMax;
		}

		private DeviceModel(DeviceType type, int powerMaxWatt) {
			this(type, powerMaxWatt, 19, 60);
		}

		public DeviceType getType() {
			return type;
		}

		public int getPowerMaxWatt() {
			return powerMaxWatt;
		}

		public int getScaldTemperatureMin() {
			return scaldTemperatureMin;
		}

		public int getScaldTemperatureMax() {
			return scaldTemperatureMax;
		}

		/**
		 * Returns the {@link DeviceModel} from the {@link Device#id}.
		 * 
		 * @param deviceId
		 * @return {@code null} if device is not remote-controllable
		 */
		public static DeviceModel getModel(Device device) {
			assert device != null;
			assert device.id != null && device.id.length() == 10;
			String typeIdStr = device.id.substring(0, 4);
			int typeId = Integer.parseInt(typeIdStr, 16);
			int deviceClassId = typeId >> 13;
			@SuppressWarnings("unused")
			int variant = typeId & 0x1FFF;

			// TODO implement real mapping (deviceClassId, variant) => DeviceModel.

			DeviceType type = DeviceType.fromId(deviceClassId);
			switch (type) {
			/* No remote controllable => not suitable for ELM. */
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
				return DeviceModel.MCX6;
			case TYPE_5:
				return DeviceModel.DSX;
			default:
				throw new IllegalArgumentException(type.toString());
			}
		}
	}

}
