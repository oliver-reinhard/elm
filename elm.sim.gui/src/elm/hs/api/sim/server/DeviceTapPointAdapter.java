package elm.hs.api.sim.server;

import elm.hs.api.model.Device;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.TapPoint;
import elm.sim.model.HotWaterTemperature;

public class DeviceTapPointAdapter implements SimModelListener {

	private final Device device;
	private final TapPoint point;
	public DeviceTapPointAdapter(TapPoint point, Device device) throws UnsupportedDeviceModelException {
		assert point != null;
		assert device != null;
		assert device.status != null;
		this.point = point;
		point.addModelListener(this);
		this.device = device;
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
		case INTAKE_WATER_TEMPERATURE:
		case SCALD_PROTECTION_TEMPERATURE:
			device.status.power = point.getPowerUnits();
			device.setHeaterOn(point.getFlags() == 0);
			device.status.flow = (short) (point.getActualFlow().getMillilitresPerMinute() / 100);
			break;
		case REFERENCE_TEMPERATURE:
			short setpoint = ((HotWaterTemperature) event.getNewValue()).getUnits();
			assert setpoint >= point.getDeviceModel().getTemperatureOff();
			assert setpoint <= point.getDeviceModel().getTemperatureMax();
			device.setSetpoint(setpoint);
			break;
		default:
			// ignore
			break;
		}
	}

	public void updateTapPoint() {
		point.setReferenceTemperature(HotWaterTemperature.fromInt(device.status.setpoint / 10));
		point.setIntakeWaterTemperature(IntakeWaterTemperature.fromShort(device.status.tIn));
	}

}
