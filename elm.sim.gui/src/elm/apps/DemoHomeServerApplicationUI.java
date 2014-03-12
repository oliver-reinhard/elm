package elm.apps;

import java.net.InetAddress;
import java.net.UnknownHostException;

import elm.hs.api.sim.server.SimHomeServerServiceImpl;
import elm.scheduler.model.UnsupportedDeviceModelException;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.TapPoint;
import elm.sim.model.impl.TapPointImpl;
import elm.sim.ui.AbstractSimServerApplicationConfiguration;

public class DemoHomeServerApplicationUI extends SimHomeServerApplicationUI {
	
 static class DemoApplicationConfiguration extends AbstractSimServerApplicationConfiguration {

		private static final String REAL_POINT_A_1 = "2016406C30";
		private static final String REAL_POINT_B_1 = "2016406C3E";
		private static final String REAL_POINT_B_2 = "2016406C31";
		
		private static final String SIM_POINT_C_1 = "2016FF0001";
		private static final String SIM_POINT_C_2 = "A001FF0002";
		private static final String SIM_POINT_C_3 = "2016FF0003";

		@Override
		public void init(boolean createSimpleScheduler, int serverPort) throws UnsupportedDeviceModelException, UnknownHostException {
			assert ! createSimpleScheduler;
			assert serverPort > 0;
			final TapPoint point_A_1 = new TapPointImpl("2 OG lk - Lavabo", REAL_POINT_A_1, false, HotWaterTemperature.TEMP_38); // real device
			final TapPoint point_B_1 = new TapPointImpl("2 OG mi - Lavabo", REAL_POINT_B_1, false, HotWaterTemperature.TEMP_38); // real device
			final TapPoint point_B_2 = new TapPointImpl("2 OG rt - Lavabo", REAL_POINT_B_2, false, HotWaterTemperature.TEMP_38); // real device
			final TapPoint point_C_1 = new TapPointImpl("1 OG lk - Dusche", SIM_POINT_C_1, true, HotWaterTemperature.TEMP_38); // sim device
			final TapPoint point_C_2 = new TapPointImpl("1 OG lk - Küche", SIM_POINT_C_2, true, HotWaterTemperature.TEMP_38); // sim device
			final TapPoint point_C_3 = new TapPointImpl("1 OG rt - Dusche", SIM_POINT_C_3, true, HotWaterTemperature.TEMP_38); // sim device

			tapPoints = new TapPoint[][] { { point_A_1, point_B_1, point_B_2 }, { point_C_1, point_C_2, point_C_3 } };

			final String uri = "http://" + InetAddress.getLocalHost().getHostName() + ":" + serverPort;
			final SimHomeServerServiceImpl server = new SimHomeServerServiceImpl(uri);
			server.addDevice(REAL_POINT_A_1, (short) 380, point_A_1);
			server.addDevice(REAL_POINT_B_1, (short) 380, point_B_1);
			server.addDevice(REAL_POINT_B_2, (short) 380, point_B_2);
			server.addDevice(SIM_POINT_C_1, (short) 380, point_C_1);
			server.addDevice(SIM_POINT_C_2, (short) 420, point_C_2);
			server.addDevice(SIM_POINT_C_3, (short) 450, point_C_3);
			this.server = server;

			scheduler = null;
		}
	}

	public static void main(String[] args) {
		run(new DemoApplicationConfiguration(), "Home Server C", 1100, 600);
	}

}
