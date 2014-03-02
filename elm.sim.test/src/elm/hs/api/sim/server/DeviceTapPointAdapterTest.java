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
import elm.hs.api.model.ElmStatus;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.scheduler.model.impl.ModelTestUtil;
import elm.sim.model.Flow;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.TapPoint;
import elm.sim.model.impl.TapPointImpl;

@RunWith(MockitoJUnitRunner.class)
public class DeviceTapPointAdapterTest {

	static final short INITIAL_TEMPERATURE = (short) (HotWaterTemperature.TEMP_2.getDegreesCelsius() * 10);

	TapPoint point;
	Device device;
	DeviceTapPointAdapter adapter;

	@Before
	public void setup() {
		try {
			device = ModelTestUtil.createDeviceWithStatus(1, 1, 0);
			point = new TapPointImpl("Dusche", device.id, true, HotWaterTemperature.TEMP_2);
			point.setStatus(ElmStatus.ON);
			final Device device2 = ModelTestUtil.createDeviceWithInfo(1, 1);
			device.info = device2.info;
			device.status.setpoint = INITIAL_TEMPERATURE;
			adapter = new DeviceTapPointAdapter(point, device);
		} catch (UnsupportedDeviceModelException e) {
			fail(e.getMessage());
			e.printStackTrace();
		}
	}

	@Test
	public void setpointAtDevice() {
		// change at point, check at device
		assertEquals(INITIAL_TEMPERATURE, device.status.setpoint);
		assertEquals(HotWaterTemperature.TEMP_2, point.getReferenceTemperature());
		assertEquals(HotWaterTemperature.TEMP_2, point.getActualTemperature());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_3);
		assertEquals(HotWaterTemperature.TEMP_3.getDegreesCelsius() * 10, device.info.setpoint);
		assertEquals(HotWaterTemperature.TEMP_3.getDegreesCelsius() * 10, device.status.setpoint);
	}

	@Test
	public void setpointAtTapPoint() {
		// change at device, check at point
		assertEquals(HotWaterTemperature.TEMP_2, point.getReferenceTemperature());

		device.setSetpoint((short) (HotWaterTemperature.TEMP_3.getDegreesCelsius() * 10));
		adapter.updateTapPoint();
		assertEquals(HotWaterTemperature.TEMP_3, point.getReferenceTemperature());
		assertEquals(HotWaterTemperature.TEMP_3, point.getActualTemperature());
	}

	@Test
	public void heaterOnAtDevice() {
		assertEquals(HotWaterTemperature.TEMP_0.getDegreesCelsius(), IntakeWaterTemperature.TEMP_25.getDegreesCelsius());
		
		device.status.tIn = (short) (IntakeWaterTemperature.TEMP_25.getDegreesCelsius() * 10);
		device.status.setpoint = device.status.tIn;  // == HotWaterTemperature.TEMP_0
		adapter.updateTapPoint();
		assertEquals(IntakeWaterTemperature.TEMP_25, point.getIntakeWaterTemperature());
		assertEquals(HotWaterTemperature.TEMP_0, point.getReferenceTemperature());
		assertEquals(HotWaterTemperature.TEMP_0, point.getActualTemperature());
		assertEquals(Flow.NONE, point.getActualFlow());
		assertFalse(device._isHeaterOn());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_2);
		assertFalse(device._isHeaterOn());

		point.setReferenceFlow(Flow.MAX);
		assertTrue(device._isHeaterOn());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_MIN);
		assertEquals(HotWaterTemperature.TEMP_0, point.getActualTemperature());  // intake water is still TEMP_25 == TEMP_0
		assertFalse(device._isHeaterOn());
	}

	@Test
	public void powerUnits() {
		device.status.tIn = (short) (HotWaterTemperature.TEMP_MIN.getDegreesCelsius() * 10);
		device.status.setpoint = device.status.tIn;
		adapter.updateTapPoint();

		point.setReferenceFlow(Flow.NONE); // flow OFF
		assertEquals(0, device.status.power); // no power consumption

		point.setReferenceFlow(Flow.MAX); //
		assertEquals(Flow.MAX, point.getActualFlow()); // flow OFF
		assertEquals(0, device.status.power); // no power consumption ( tRef == tIn)

		point.setReferenceTemperature(HotWaterTemperature.TEMP_2);
		assertEquals(HotWaterTemperature.TEMP_2, point.getActualTemperature());
		assertEquals(123, device.status.power); // power units
	}

}
