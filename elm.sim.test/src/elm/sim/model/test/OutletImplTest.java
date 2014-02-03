package elm.sim.model.test;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import elm.sim.model.Flow;
import static elm.sim.model.Flow.*;
import elm.sim.model.TapPoint;
import elm.sim.model.Status;
import static elm.sim.model.Status.*;
import elm.sim.model.Temperature;
import static elm.sim.model.Temperature.*;
import elm.sim.model.impl.TapPointImpl;

public class OutletImplTest {

	private static final Temperature INITIAL_REFERENCE_TEMP = TEMP_3;

	private TapPoint o;

	@Before
	public void setup() {
		o = new TapPointImpl("Name", INITIAL_REFERENCE_TEMP);

	}

	@Test
	public void setServerStatusNoActualFlow() {
		assertStatus(OFF, TEMP_MIN, NONE);
		// Full life cycle:
		o.setSchedulerStatus(ON); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, NONE);

		o.setSchedulerStatus(SATURATION);
		assertStatus(SATURATION, INITIAL_REFERENCE_TEMP, NONE);

		o.setSchedulerStatus(OVERLOAD);
		assertStatus(OVERLOAD, TEMP_MIN, NONE);

		o.setSchedulerStatus(ERROR);
		assertStatus(ERROR, TEMP_MIN, NONE);

		o.setSchedulerStatus(OVERLOAD);
		assertStatus(OVERLOAD, TEMP_MIN, NONE);

		o.setSchedulerStatus(SATURATION);
		assertStatus(SATURATION, INITIAL_REFERENCE_TEMP, NONE);

		o.setSchedulerStatus(OFF); // turn OFF
		assertStatus(OFF, TEMP_MIN, NONE);
	}

	@Test
	public void setServerStatusDuringActualFlow() {
		assertStatus(OFF, TEMP_MIN, NONE);

		o.setReferenceFlow(MEDIUM); // turn on
		assertStatus(OFF, TEMP_MIN, MEDIUM); // stays cold

		// Full life cycle:
		o.setSchedulerStatus(ON);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);  // now warm

		o.setSchedulerStatus(SATURATION);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setSchedulerStatus(OVERLOAD);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setSchedulerStatus(ERROR);
		assertStatus(ERROR, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setSchedulerStatus(OVERLOAD);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setSchedulerStatus(SATURATION);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setSchedulerStatus(OFF);
		assertStatus(OFF, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setReferenceFlow(NONE); // turn OFF
		assertStatus(OFF, TEMP_MIN, NONE);
	}

	@Test
	public void setServerStatus_EndActualFlow_Off() {
		assertStatus(OFF, TEMP_MIN, NONE);

		o.setReferenceFlow(MEDIUM); // turn on
		assertStatus(OFF, TEMP_MIN, MEDIUM); // stays cold

		o.setReferenceFlow(NONE); // turn OFF
		assertStatus(OFF, TEMP_MIN, NONE);
	}

	@Test
	public void setServerStatus_EndActualFlow_On() {
		assertStatus(OFF, TEMP_MIN, NONE);

		o.setSchedulerStatus(ON);
		o.setReferenceFlow(MEDIUM); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setReferenceFlow(NONE); // turn OFF
		assertStatus(ON, INITIAL_REFERENCE_TEMP, NONE);
	}

	@Test
	public void setServerStatus_EndActualFlow_Saturation() {
		assertStatus(OFF, TEMP_MIN, NONE);

		o.setSchedulerStatus(SATURATION);
		o.setReferenceFlow(MEDIUM); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setReferenceTemperature(TEMP_4);
		assertStatus(ON, TEMP_4, MEDIUM);

		o.setReferenceTemperature(TEMP_3);
		assertStatus(ON, TEMP_3, MEDIUM);

		o.setReferenceFlow(NONE); // turn OFF
		assertStatus(SATURATION, INITIAL_REFERENCE_TEMP, NONE);
	}

	@Test
	public void setServerStatus_EndActualFlow_Overload() {
		assertStatus(OFF, TEMP_MIN, NONE);

		o.setSchedulerStatus(SATURATION);
		o.setReferenceFlow(MEDIUM); // turn on
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setSchedulerStatus(OVERLOAD);
		assertStatus(ON, INITIAL_REFERENCE_TEMP, MEDIUM);

		o.setReferenceTemperature(TEMP_4);
		assertStatus(ON, TEMP_4, MEDIUM);

		o.setReferenceTemperature(TEMP_3);
		assertStatus(ON, TEMP_3, MEDIUM);

		o.setReferenceFlow(NONE); // turn OFF
		assertStatus(OVERLOAD, TEMP_MIN, NONE);
	}

	@Test
	public void setServerStatus_EndActualFlow_Error() {
		assertStatus(OFF, TEMP_MIN, NONE);

		o.setSchedulerStatus(SATURATION);
		o.setReferenceFlow(MEDIUM); // turn on
		o.setReferenceTemperature(TEMP_3);
		assertStatus(ON, TEMP_3, MEDIUM);

		o.setSchedulerStatus(ERROR);
		assertStatus(ERROR, TEMP_3, MEDIUM);

		o.setReferenceTemperature(TEMP_4);
		assertStatus(ERROR, TEMP_3, MEDIUM);

		o.setReferenceTemperature(TEMP_3);
		assertStatus(ERROR, TEMP_3, MEDIUM);

		o.setReferenceTemperature(TEMP_2);
		assertStatus(ERROR, TEMP_2, MEDIUM);

		o.setReferenceTemperature(TEMP_3);
		assertStatus(ERROR, TEMP_2, MEDIUM);

		o.setReferenceTemperature(TEMP_1);
		assertStatus(ERROR, TEMP_1, MEDIUM);

		o.setReferenceFlow(NONE); // turn OFF
		assertStatus(ERROR, TEMP_MIN, NONE);
	}

	private void assertStatus(Status expectedStatus, Temperature expectedActualTemp, Flow expectedActualFlow) {
		assertEquals(expectedStatus, o.getStatus());
		assertEquals(expectedActualTemp, o.getActualTemperature());
		assertEquals(expectedActualFlow, o.getActualFlow());
	}
}
