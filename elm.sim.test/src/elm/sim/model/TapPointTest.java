package elm.sim.model;

import static elm.sim.model.Flow.MEDIUM;
import static elm.sim.model.Flow.NONE;
import static elm.sim.model.HotWaterTemperature.TEMP_1;
import static elm.sim.model.HotWaterTemperature.TEMP_2;
import static elm.sim.model.HotWaterTemperature.TEMP_3;
import static elm.sim.model.HotWaterTemperature.TEMP_4;
import static elm.sim.model.HotWaterTemperature.TEMP_MIN;
import static elm.sim.model.SimStatus.ERROR;
import static elm.sim.model.SimStatus.OFF;
import static elm.sim.model.SimStatus.ON;
import static elm.sim.model.SimStatus.OVERLOAD;
import static elm.sim.model.SimStatus.SATURATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import elm.hs.api.model.Device;
import elm.scheduler.model.UnsupportedModelException;
import elm.scheduler.model.impl.ModelTestUtil;
import elm.sim.model.impl.TapPointImpl;

public class TapPointTest {

	private static final HotWaterTemperature INITIAL_REFERENCE_TEMP = TEMP_3;

	private TapPoint point;

	@Before
	public void setup() {
		try {
			Device device = ModelTestUtil.createDeviceWithStatus(1, 1, 0);
			point = new TapPointImpl("Name", device.id, true, INITIAL_REFERENCE_TEMP);
			point.setSchedulerStatus(OFF);
			assertStatus(OFF, TEMP_MIN, NONE);
		} catch (UnsupportedModelException e) {
			fail(e.getMessage());
			e.printStackTrace();
		}
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
		assertStatus(OVERLOAD, TEMP_MIN, NONE);

		point.setSchedulerStatus(ERROR);
		assertStatus(ERROR, TEMP_MIN, NONE);

		point.setSchedulerStatus(OVERLOAD);
		assertStatus(OVERLOAD, TEMP_MIN, NONE);

		point.setSchedulerStatus(SATURATION);
		assertStatus(SATURATION, INITIAL_REFERENCE_TEMP, NONE);

		point.setSchedulerStatus(OFF); // turn OFF
		assertStatus(OFF, TEMP_MIN, NONE);
	}

	@Test
	public void setSchedulerStatus_DuringActualFlow() {
		point.setReferenceFlow(MEDIUM); // turn on
		assertStatus(OFF, TEMP_MIN, MEDIUM); // stays cold

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
		assertStatus(OFF, TEMP_MIN, NONE);
	}

	@Test
	public void setSchedulerStatus_EndActualFlow_Off() {
		point.setReferenceFlow(MEDIUM); // turn on
		assertStatus(OFF, TEMP_MIN, MEDIUM); // stays cold

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(OFF, TEMP_MIN, NONE);
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

		point.setReferenceTemperature(TEMP_4);
		assertStatus(ON, TEMP_4, MEDIUM);

		point.setReferenceTemperature(TEMP_3);
		assertStatus(ON, TEMP_3, MEDIUM);

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

		point.setReferenceTemperature(TEMP_4);
		assertStatus(ON, TEMP_4, MEDIUM);

		point.setReferenceTemperature(TEMP_3);
		assertStatus(ON, TEMP_3, MEDIUM);

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(OVERLOAD, TEMP_MIN, NONE);
	}

	@Test
	public void setSchedulerStatus_EndActualFlow_Error() {
		point.setSchedulerStatus(SATURATION);
		point.setReferenceFlow(MEDIUM); // turn ON
		point.setReferenceTemperature(TEMP_3);
		assertStatus(ON, TEMP_3, MEDIUM);

		point.setSchedulerStatus(ERROR);
		assertStatus(ERROR, TEMP_3, MEDIUM);

		point.setReferenceTemperature(TEMP_4);
		assertStatus(ERROR, TEMP_3, MEDIUM);

		point.setReferenceTemperature(TEMP_3);
		assertStatus(ERROR, TEMP_3, MEDIUM);

		point.setReferenceTemperature(TEMP_2);
		assertStatus(ERROR, TEMP_2, MEDIUM);

		point.setReferenceTemperature(TEMP_3);
		assertStatus(ERROR, TEMP_2, MEDIUM);

		point.setReferenceTemperature(TEMP_1);
		assertStatus(ERROR, TEMP_1, MEDIUM);

		point.setReferenceFlow(NONE); // turn OFF
		assertStatus(ERROR, TEMP_MIN, NONE);
	}

	private void assertStatus(SimStatus expectedStatus, HotWaterTemperature expectedActualTemp, Flow expectedActualFlow) {
		assertEquals(expectedStatus, point.getStatus());
		assertEquals(expectedActualTemp, point.getActualTemperature());
		assertEquals(expectedActualFlow, point.getActualFlow());
	}
}
