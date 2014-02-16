package elm.hs.api.sim.server;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.model.UnsupportedModelException;
import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.TapPoint;
import elm.sim.model.Temperature;

public class DeviceTapPointAdapter implements SimModelListener {

	private final Device device;
	private final TapPoint point;
	private final DeviceModel deviceModel;

	public DeviceTapPointAdapter(TapPoint point, Device device) throws UnsupportedModelException {
		assert point != null;
		assert device != null;
		this.point = point;
		point.addModelListener(this);
		this.device = device;
		deviceModel = DeviceModel.getModel(device);
		if (!deviceModel.getType().isRemoteControllable()) {
			throw new UnsupportedModelException(device.id);
		}
		updateTapPoint();
	}

	public Device getDevice() {
		return device;
	}

	public TapPoint getPoint() {
		return point;
	}

	@Override
	public void modelChanged(SimModelEvent event) {
		switch ((TapPoint.Attribute) event.getAttribute()) {
		case ACTUAL_TEMPERATURE:
		case ACTUAL_FLOW:
			final boolean heaterOn = point.getActualFlow().isOn() && point.getActualTemperature().getDegreesCelsius() * 10 > device.status.tIn;
			device.setHeaterOn(heaterOn);
			if (heaterOn) {
				int powerWatt = (int) ((point.getActualTemperature().getDegreesCelsius() - device.status.tIn / 10.0)
						* point.getActualFlow().getMillilitresPerMinute() / 60 * 4.192);
				powerWatt = Math.min(powerWatt, deviceModel.getPowerMaxWatt());
				device.status.power = toPowerUnits(powerWatt);
			} else {
				device.status.power = 0;
			}
			break;
		case REFERENCE_TEMPERATURE:
			device.setSetpoint((short) (((Temperature) event.getNewValue()).getDegreesCelsius() * 10));
			break;
		default:
			// ignore
			break;
		}
	}

	public void updateTapPoint() {
		point.setReferenceTemperature(Temperature.fromInt(device.status.setpoint / 10));
	}

	int toPowerWatt(short powerUnits) {
		assert device.status.powerMax != 0;
		return deviceModel.getPowerMaxWatt() * powerUnits / device.status.powerMax;
	}

	short toPowerUnits(int powerWatt) {
		assert device.status.powerMax != 0;
		return (short) (powerWatt * device.status.powerMax / deviceModel.getPowerMaxWatt());
	}

}
