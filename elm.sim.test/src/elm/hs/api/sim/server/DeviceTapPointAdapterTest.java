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
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.impl.TapPointImpl;
import elm.ui.api.ElmStatus;

@RunWith(MockitoJUnitRunner.class)
public class DeviceTapPointAdapterTest {

	static final short INITIAL_TEMPERATURE = (short) (HotWaterTemperature.TEMP_2.getDegreesCelsius() * 10);

	TapPointImpl point;
	Device device;
	DeviceTapPointAdapter adapter;

	@Before
	public void setup() {
		try {
			device = ModelTestUtil.createDeviceWithStatus(1, 1, 0);
			point = new TapPointImpl("Dusche", device.id, HotWaterTemperature.TEMP_2);
			point.setStatus(ElmStatus.ON);
			final Device device2 = ModelTestUtil.createDeviceWithInfo(1, 1);
			device.info = device2.info;
			device.status.setpoint = INITIAL_TEMPERATURE;
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
		device.status.tIn = (short) (HotWaterTemperature.TEMP_MIN.getDegreesCelsius() * 10);
		device.status.setpoint = device.status.tIn;
		adapter.updateTapPoint();
		assertEquals(HotWaterTemperature.TEMP_MIN.getDegreesCelsius(), point.getIntakeWaterTemperature().getDegreesCelsius());
		assertEquals(HotWaterTemperature.TEMP_MIN, point.getReferenceTemperature());
		assertEquals(HotWaterTemperature.TEMP_MIN, point.getActualTemperature());
		assertEquals(Flow.NONE, point.getActualFlow());
		assertFalse(device._isHeaterOn());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_2);
		assertFalse(device._isHeaterOn());

		point.setReferenceFlow(Flow.MAX);
		assertTrue(device._isHeaterOn());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_MIN);
		assertEquals(HotWaterTemperature.TEMP_MIN, point.getActualTemperature());
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
		assertEquals(157, device.status.power); // power units
	}

}
