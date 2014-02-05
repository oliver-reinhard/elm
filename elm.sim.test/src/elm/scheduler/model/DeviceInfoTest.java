package elm.scheduler.model;

import static elm.scheduler.model.ModelTestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import elm.scheduler.model.DeviceInfo.State;
import elm.scheduler.model.DeviceInfo.UpdateResult;
import elm.scheduler.model.impl.DeviceInfoImpl;

public class DeviceInfoTest {

	static final String ID = "d1";

	HomeServer hs1;
	DeviceInfo di1;

	@Before
	public void setup() {
		hs1 = mock(HomeServer.class);
		try {
			di1 = new DeviceInfoImpl(hs1, createDevice(1, 1, 0), ID);
		} catch (UnsupportedModelException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Test
	public void test() {
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(0, di1.getActualPowerWatt());
		assertEquals(State.READY, di1.getState());

		UpdateResult result = di1.update(createDevice(1, 1, 10_000));
		assertEquals(round(10_000), di1.getDemandPowerWatt());
		assertEquals(0, di1.getActualPowerWatt());
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(State.CONSUMING, di1.getState());

		result = di1.update(createDevice(1, 1, 0));
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(0, di1.getActualPowerWatt());
		assertEquals(UpdateResult.MINOR_UPDATES, result);
		assertEquals(State.READY, di1.getState());
	}
}
