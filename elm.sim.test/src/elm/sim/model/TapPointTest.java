package elm.sim.model;

import static elm.scheduler.model.impl.ModelTestUtil.FLOW_OFF;
import static elm.sim.model.Flow.MEDIUM;
import static elm.sim.model.Flow.NONE;
import static elm.sim.model.HotWaterTemperature.TEMP_25;
import static elm.sim.model.HotWaterTemperature.TEMP_30;
import static elm.sim.model.HotWaterTemperature.TEMP_38;
import static elm.sim.model.HotWaterTemperature.TEMP_42;
import static elm.sim.model.HotWaterTemperature.TEMP_48;
import static elm.sim.model.HotWaterTemperature.TEMP_MIN_19;
import static elm.sim.model.SimStatus.ERROR;
import static elm.sim.model.SimStatus.OFF;
import static elm.sim.model.SimStatus.ON;
import static elm.sim.model.SimStatus.OVERLOAD;
import static elm.sim.model.SimStatus.SATURATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import elm.hs.api.Device;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.scheduler.model.impl.ModelTestUtil;
import elm.sim.model.impl.TapPointImpl;

public class TapPointTest {

	private static final HotWaterTemperature INITIAL_REFERENCE_TEMP = TEMP_42;

	private TapPoint point;

	@Before
	public void setup() {
		try {
			Device device = ModelTestUtil.createDeviceWithStatus(1, 1, 0, FLOW_OFF);
			point = new TapPointImpl("Name", device.id, true, INITIAL_REFERENCE_TEMP);
			point.setSchedulerStatus(OFF);
			assertStatus(OFF, TEMP_MIN_19, NONE);
		} catch (UnsupportedDeviceModelException e) {
			fail(e.getMessage());
			e.printStackTrace();
		}
	}

	@Test
	public void setActualTemperature() {
		point.setReferenceTemperature(TEMP_MIN_19);
		assertEquals(TEMP_MIN_19, point.getActualTemperature());
		
		assert IntakeWaterTemperature.TEMP_15.getDegreesCelsius() < TEMP_MIN_19.getDegreesCelsius();
		point.setIntakeWaterTemperature(IntakeWaterTemperature.TEMP_15);
		assertEquals(TEMP_MIN_19, point.getActualTemperature());

		assert IntakeWaterTemperature.TEMP_25.getDegreesCelsius() > TEMP_MIN_19.getDegreesCelsius();
		point.setIntakeWaterTemperature(IntakeWaterTemperature.TEMP_25);
		assertEquals(TEMP_25, point.getActualTemperature());
	}

	//
	// -------- Changing the SCHEDULER status: --------
	//
	@Test
	public void setSchedulerStatus_NoActualFlow() {
		// Full life cycle:
		point.setSchedulerStatus(ON); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, NONE);

		point.setSchedulerStatus(SATURATION);
		assertStatus(SATURATION, INITIAL_REFERENCE_TEMP, NONE);

		point.setSchedulerStatus(OVERLOAD);
		assertStatus(OVERLOAD, TEMP_MIN_19, NONE);

		point.setSchedulerStatus(ERROR);
		assertStatus(ERROR, TEMP_MIN_19, NONE);

		point.setSchedulerStatus(OVERLOAD);
		assertStatus(OVERLOAD, TEMP_MIN_19, NONE);

		point.setSchedulerStatus(SATURATION);
		assertStatus(SATURATION, INITIAL_REFERENCE_TEMP, NONE);

		point.setSchedulerStatus(OFF); // turn OFF
		assertStatus(OFF, TEMP_MIN_19, NONE);
	}

	@Test
	public void setSchedulerStatus_DuringActualFlow() {
		point.setReferenceFlow(MEDIUM); // turn on
		assertStatus(OFF, TEMP_MIN_19, MEDIUM); // stays cold

		// Full life cycle:
		point.setSchedulerStatus(ON);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM); // now warm

		point.setSchedulerStatus(SATURATION);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setSchedulerStatus(OVERLOAD);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setSchedulerStatus(ERROR);
		assertStatus(ERROR, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setSchedulerStatus(OVERLOAD);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setSchedulerStatus(SATURATION);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setSchedulerStatus(OFF);
		assertStatus(OFF, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(OFF, TEMP_MIN_19, NONE);
	}

	@Test
	public void setSchedulerStatus_EndActualFlow_Off() {
		point.setReferenceFlow(MEDIUM); // turn on
		assertStatus(OFF, TEMP_MIN_19, MEDIUM); // stays cold

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(OFF, TEMP_MIN_19, NONE);
	}

	@Test
	public void setSchedulerStatus_EndActualFlow_On() {
		point.setSchedulerStatus(ON);
		point.setReferenceFlow(MEDIUM); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(ON, INITIAL_REFERENCE_TEMP, NONE);
	}

	@Test
	public void setSchedulerStatus_EndActualFlow_Saturation() {
		point.setSchedulerStatus(SATURATION);
		point.setReferenceFlow(MEDIUM); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setReferenceTemperature(TEMP_48);
		assertStatus(ON, TEMP_48, MEDIUM);

		point.setReferenceTemperature(TEMP_42);
		assertStatus(ON, TEMP_42, MEDIUM);

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(SATURATION, INITIAL_REFERENCE_TEMP, NONE);
	}

	@Test
	public void setSchedulerStatus_EndActualFlow_Overload() {
		point.setSchedulerStatus(SATURATION);
		point.setReferenceFlow(MEDIUM); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setSchedulerStatus(OVERLOAD);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		point.setReferenceTemperature(TEMP_48);
		assertStatus(ON, TEMP_48, MEDIUM);

		point.setReferenceTemperature(TEMP_42);
		assertStatus(ON, TEMP_42, MEDIUM);

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(OVERLOAD, TEMP_MIN_19, NONE);
	}

	@Test
	public void setSchedulerStatus_EndActualFlow_Error() {
		point.setSchedulerStatus(SATURATION);
		point.setReferenceFlow(MEDIUM); // turn ON
		point.setReferenceTemperature(TEMP_42);
		assertStatus(ON, TEMP_42, MEDIUM);

		point.setSchedulerStatus(ERROR);
		assertStatus(ERROR, TEMP_42, MEDIUM);

		point.setReferenceTemperature(TEMP_48);
		assertStatus(ERROR, TEMP_42, MEDIUM);

		point.setReferenceTemperature(TEMP_42);
		assertStatus(ERROR, TEMP_42, MEDIUM);

		point.setReferenceTemperature(TEMP_38);
		assertStatus(ERROR, TEMP_38, MEDIUM);

		point.setReferenceTemperature(TEMP_42);
		assertStatus(ERROR, TEMP_38, MEDIUM);

		point.setReferenceTemperature(TEMP_30);
		assertStatus(ERROR, TEMP_30, MEDIUM);

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(ERROR, TEMP_MIN_19, NONE);
	}

	private void assertStatus(SimStatus expectedStatus, HotWaterTemperature expectedActualTemp, Flow expectedActualFlow) {
		assertEquals(expectedStatus, point.getStatus());
		assertEquals(expectedActualTemp, point.getActualTemperature());
		assertEquals(expectedActualFlow, point.getActualFlow());
	}
}
