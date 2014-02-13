package elm.sim.hs.server;

import elm.hs.api.model.Device;
import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.TapPoint;
import elm.sim.model.Temperature;

public class DeviceTapPointAdapter implements SimModelListener {
	
	private final Device device;
	private final TapPoint point;

	public DeviceTapPointAdapter(TapPoint point, Device device) {
		assert point != null;
		assert device != null;
		this.point = point;
		point.addModelListener(this);
		this.device = device;
		point.setReferenceTemperature(Temperature.fromInt(device.status.setpoint / 10));
	}

	@Override
	public void modelChanged(SimModelEvent event) {
		switch((TapPoint.Attribute) event.getAttribute()) {
		case ACTUAL_FLOW:
			break;
		case ACTUAL_TEMPERATURE:
			break;
		case NAME:
			break;
		case REFERENCE_FLOW:
			break;
		case REFERENCE_TEMPERATURE:
			device.status.setpoint = (short) (((int) event.getNewValue()) * 10);
			break;
		case SCALD_TEMPERATURE:
			break;
		case STATUS:
			break;
		case WAITING_TIME_PERCENT:
			break;
		default:
			throw new IllegalArgumentException(event.getAttribute().id());
		
		}
	}
	
	public void updateTapPoint() {
		point.setActualTemperature(Temperature.fromInt(device.status.setpoint / 10));
	}

}
