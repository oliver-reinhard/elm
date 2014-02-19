package elm.hs.api.sim.server;

import elm.hs.api.model.Device;
import elm.scheduler.model.UnsupportedModelException;
import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.TapPoint;
import elm.sim.model.HotWaterTemperature;

public class DeviceTapPointAdapter implements SimModelListener {

	private final Device device;
	private final TapPoint point;
	public DeviceTapPointAdapter(TapPoint point, Device device) throws UnsupportedModelException {
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
			device.status.power = point.getPowerUnits();
			device.setHeaterOn(device.status.power > 0);
			break;
		case REFERENCE_TEMPERATURE:
			device.setSetpoint(((HotWaterTemperature) event.getNewValue()).getUnits());
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
