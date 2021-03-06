package elm.hs.api.sim.server;

import static elm.scheduler.model.impl.ModelTestUtil.FLOW_OFF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import elm.hs.api.Device;
import elm.hs.api.ElmStatus;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.scheduler.model.impl.ModelTestUtil;
import elm.sim.model.Flow;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.TapPoint;
import elm.sim.model.impl.TapPointImpl;

@RunWith(MockitoJUnitRunner.class)
public class DeviceTapPointAdapterTest {

	static final short INITIAL_TEMPERATURE = (short) (HotWaterTemperature.TEMP_38.getDegreesCelsius() * 10);

	TapPoint point;
	Device device;
	DeviceTapPointAdapter adapter;

	@Before
	public void setup() {
		try {
			device = ModelTestUtil.createDeviceWithStatus(1, 1, 0, FLOW_OFF);
			point = new TapPointImpl("Dusche", device.id, true, HotWaterTemperature.TEMP_38);
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
		assertEquals(HotWaterTemperature.TEMP_38, point.getReferenceTemperature());
		assertEquals(HotWaterTemperature.TEMP_38, point.getActualTemperature());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_42);
		assertEquals(HotWaterTemperature.TEMP_42.getDegreesCelsius() * 10, device.info.setpoint);
		assertEquals(HotWaterTemperature.TEMP_42.getDegreesCelsius() * 10, device.status.setpoint);
	}

	@Test
	public void setpointAtTapPoint() {
		// change at device, check at point
		assertEquals(HotWaterTemperature.TEMP_38, point.getReferenceTemperature());

		device.setSetpoint((short) (HotWaterTemperature.TEMP_42.getDegreesCelsius() * 10));
		adapter.updateTapPoint();
		assertEquals(HotWaterTemperature.TEMP_42, point.getReferenceTemperature());
		assertEquals(HotWaterTemperature.TEMP_42, point.getActualTemperature());
	}

	@Test
	public void heaterOnAtDevice() {
		assertEquals(HotWaterTemperature.TEMP_25.getDegreesCelsius(), IntakeWaterTemperature.TEMP_25.getDegreesCelsius());
		
		device.status.tIn = (short) (IntakeWaterTemperature.TEMP_25.getDegreesCelsius() * 10);
		device.status.setpoint = device.status.tIn;  // == HotWaterTemperature.TEMP_25
		adapter.updateTapPoint();
		assertEquals(IntakeWaterTemperature.TEMP_25, point.getIntakeWaterTemperature());
		assertEquals(HotWaterTemperature.TEMP_25, point.getReferenceTemperature());
		assertEquals(HotWaterTemperature.TEMP_25, point.getActualTemperature());
		assertEquals(Flow.NONE, point.getActualFlow());
		assertEquals(1, point.getFlags());
		assertFalse(device._isHeaterOn());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_38);
		assertFalse(device._isHeaterOn());

		point.setReferenceFlow(Flow.MAX);
		assertEquals(0, point.getFlags());
		assertTrue(device._isHeaterOn());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_MIN_19);
		assertEquals(HotWaterTemperature.TEMP_25, point.getActualTemperature());  // intake water is still TEMP_25
		assertFalse(device._isHeaterOn());

		point.setReferenceTemperature(HotWaterTemperature.TEMP_38);
		assertEquals(0, point.getFlags());
		assertTrue(device._isHeaterOn());
		
		point.setReferenceFlow(Flow.NONE);
		assertEquals(1, point.getFlags());
		assertFalse(device._isHeaterOn());

		device.status.tIn = (short) (IntakeWaterTemperature.TEMP_10.getDegreesCelsius() * 10);
		adapter.updateTapPoint();
		
		point.setReferenceFlow(Flow.MIN);
		assertEquals(0, point.getFlags());
		assertTrue(device._isHeaterOn());
		
		point.setReferenceTemperature(HotWaterTemperature.TEMP_MIN_19);
		point.setScaldProtectionTemperature(HotWaterTemperature.TEMP_MIN_19);
		assertEquals(0, device.status.power);
		assertEquals(0, point.getFlags());
		assertTrue(device._isHeaterOn());
	}

	@Test
	public void powerUnits() {
		device.status.tIn = (short) (HotWaterTemperature.TEMP_MIN_19.getDegreesCelsius() * 10);
		device.status.setpoint = device.status.tIn;
		adapter.updateTapPoint();

		point.setReferenceFlow(Flow.NONE); // flow OFF
		assertEquals(0, device.status.power); // no power consumption

		point.setReferenceFlow(Flow.MAX); //
		assertEquals(Flow.MAX, point.getActualFlow()); // flow OFF
		assertEquals(0, device.status.power); // no power consumption ( tRef == tIn)

		point.setReferenceTemperature(HotWaterTemperature.TEMP_38);
		assertEquals(HotWaterTemperature.TEMP_38, point.getActualTemperature());
		assertEquals(123, device.status.power); // power units
	}

}
