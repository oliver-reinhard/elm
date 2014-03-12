package elm.sim.ui;

import java.net.InetAddress;
import java.net.UnknownHostException;

import elm.hs.api.sim.server.SimHomeServerServiceImpl;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.TapPoint;
import elm.sim.model.impl.SimpleSchedulerImpl;
import elm.sim.model.impl.TapPointImpl;

public class SimpleSimServerApplicationConfiguration extends AbstractSimServerApplicationConfiguration {

	private static final String POINT_1_ID = "2016FF0001";
	private static final String POINT_2_ID = "A001FF0002";
	private static final String POINT_3_ID = "6003FF0003";
	private static final String POINT_4_ID = "2016FF0004";

	@Override
	public void init(boolean createSimpleScheduler, int serverPort) throws UnsupportedDeviceModelException, UnknownHostException {
		assert serverPort > 0;
		final TapPoint point1 = new TapPointImpl("2 OG lk - Dusche", POINT_1_ID, false, HotWaterTemperature.TEMP_38); // "real" device
		final TapPoint point2 = new TapPointImpl("2 OG lk - Küche", POINT_2_ID, true, HotWaterTemperature.TEMP_38); // sim device
		final TapPoint point3 = new TapPointImpl("1 OG lk - Dusche", POINT_3_ID, true, HotWaterTemperature.TEMP_38); // sim device
		final TapPoint point4 = new TapPointImpl("1 OG lk - Küche", POINT_4_ID, true, HotWaterTemperature.TEMP_38); // sim device

		tapPoints = new TapPoint[][] { { point1, point2 }, { point3, point4 } };

		final String uri = "http://" + InetAddress.getLocalHost().getHostName() + ":" + serverPort;
		final SimHomeServerServiceImpl server = new SimHomeServerServiceImpl(uri);
		server.addDevice(POINT_1_ID, (short) 380, point1);
		server.addDevice(POINT_2_ID, (short) 420, point2);
		server.addDevice(POINT_3_ID, (short) 450, point3);
		server.addDevice(POINT_4_ID, (short) 300, point4);
		this.server = server;

		scheduler = createSimpleScheduler ? new SimpleSchedulerImpl() : null;
	}

}
