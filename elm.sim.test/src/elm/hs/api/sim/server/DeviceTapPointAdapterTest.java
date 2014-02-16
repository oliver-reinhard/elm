package elm.hs.api.sim.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import elm.hs.api.model.Device;
import elm.scheduler.model.UnsupportedModelException;
import elm.scheduler.model.impl.ModelTestUtil;
import elm.sim.model.Flow;
import elm.sim.model.Temperature;
import elm.sim.model.impl.TapPointImpl;
import elm.ui.api.ElmStatus;

@RunWith(MockitoJUnitRunner.class)
public class DeviceTapPointAdapterTest {
	
	static final short INITIAL_TEMPERATURE = (short) (Temperature.TEMP_2.getDegreesCelsius() * 10);
	
	TapPointImpl point;
	Device device;
	DeviceTapPointAdapter adapter;
	
	@Before
	public void setup() {
		point = new TapPointImpl("Dusche", Temperature.TEMP_2);
		point.setStatus(ElmStatus.ON);
		device = ModelTestUtil.createDeviceWithStatus(1, 1, 0);
		final Device device2 = ModelTestUtil.createDeviceWithInfo(1, 1);
		device.info = device2.info;
		device.status.setpoint = INITIAL_TEMPERATURE;
		try {
			adapter = new DeviceTapPointAdapter(point, device);
		} catch (UnsupportedModelException e) {
			fail(e.getMessage());
			e.printStackTrace();
		}
	}

	@Test
	public void setpointAtDevice() {
		// change at point, check at device
		assertEquals(INITIAL_TEMPERATURE, device.status.setpoint);
		assertEquals(Temperature.TEMP_2, point.getReferenceTemperature());
		assertEquals(Temperature.TEMP_2, point.getActualTemperature());
		
		point.setReferenceTemperature(Temperature.TEMP_3);
		assertEquals(Temperature.TEMP_3.getDegreesCelsius()*10, device.info.setpoint);
		assertEquals(Temperature.TEMP_3.getDegreesCelsius()*10, device.status.setpoint);
	}

	@Test
	public void setpointAtTapPoint() {
		// change at device, check at point
		assertEquals(Temperature.TEMP_2, point.getReferenceTemperature());
		
		device.setSetpoint((short) (Temperature.TEMP_3.getDegreesCelsius()*10));
		adapter.updateTapPoint();
		assertEquals(Temperature.TEMP_3, point.getReferenceTemperature());
		assertEquals(Temperature.TEMP_3, point.getActualTemperature());
	}

	@Test
	public void heaterOnAtDevice() {
		device.status.tIn = (short) (Temperature.TEMP_MIN.getDegreesCelsius() * 10);
		assertEquals(Temperature.TEMP_2, point.getActualTemperature());
		assertFalse(device._isHeaterOn());
		assertEquals(Flow.NONE, point.getActualFlow());
		
		point.setReferenceFlow(Flow.MAX);
		assertTrue(device._isHeaterOn());
		
		point.setReferenceTemperature(Temperature.TEMP_MIN);
		assertEquals(Temperature.TEMP_MIN, point.getActualTemperature());
		assertFalse(device._isHeaterOn());
	}

	@Test
	public void powerUnits() {
		device.status.tIn = (short) (Temperature.TEMP_MIN.getDegreesCelsius() * 10);
		point.setReferenceTemperature(Temperature.TEMP_MIN); // tRef == tIn
		assertEquals(Temperature.TEMP_MIN, point.getActualTemperature());
		point.setReferenceFlow(Flow.NONE);		// flow OFF
		assertEquals(0, device.status.power);  // no power consumption

		point.setReferenceFlow(Flow.MAX);  // 
		assertEquals(Flow.MAX, point.getActualFlow());	// flow OFF
		assertEquals(0, device.status.power); // no power consumption ( tRef == tIn)

		point.setReferenceTemperature(Temperature.TEMP_2);
		assertEquals(Temperature.TEMP_2, point.getActualTemperature());
		assertEquals(157, device.status.power); // power units
	} 

}
