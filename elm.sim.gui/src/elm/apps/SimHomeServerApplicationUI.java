package elm.apps;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import elm.hs.api.sim.server.SimHomeServer;
import elm.hs.api.sim.server.SimHomeServerImpl;
import elm.hs.api.sim.server.SimHomeServerServer;
import elm.scheduler.model.UnsupportedModelException;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.impl.TapPointImpl;
import elm.sim.ui.AbstractTapPointUI;
import elm.sim.ui.EnumSelectorPanel;
import elm.sim.ui.RealTapPointUI;
import elm.sim.ui.SimTapPointUI;
import elm.util.ElmLogFormatter;

@SuppressWarnings("serial")
public class SimHomeServerApplicationUI extends JFrame {

	private static final String POINT_1_ID = "A001FF0001";
	private static final String POINT_2_ID = "A001FF0002";
	private static final String POINT_3_ID = "6003FF0003";
	private static final String POINT_4_ID = "2016FF0005";

	class IntakeWaterTemperaturePanel extends EnumSelectorPanel<IntakeWaterTemperature> {

		IntakeWaterTemperaturePanel() {
			super("Kaltwasser", false, IntakeWaterTemperature.TEMP_5, IntakeWaterTemperature.TEMP_10, IntakeWaterTemperature.TEMP_15,
					IntakeWaterTemperature.TEMP_20);
		}

		@Override
		protected void referenceValueChanged(IntakeWaterTemperature newValue) {
			server.setIntakeWaterTemperature(newValue);
		}
	}

//	private SimpleSchedulerUI scheduler;
	private SimHomeServer server;
	private AbstractTapPointUI tapPoint_1;
	private AbstractTapPointUI tapPoint_2;
	private AbstractTapPointUI tapPoint_3;
	private AbstractTapPointUI tapPoint_4;

	public SimHomeServerApplicationUI(SimHomeServer server, TapPointImpl point1, TapPointImpl point2, TapPointImpl point3, TapPointImpl point4)
			throws UnsupportedModelException {
		this.server = server;
		setTitle("Dusche");
		setSize(800, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);

//		scheduler = new SimpleSchedulerUI(new SimpleSchedulerImpl());
//		GridBagConstraints gbc_scheduler = new GridBagConstraints();
//		gbc_scheduler.insets = new Insets(5, 5, 5, 5);
//		gbc_scheduler.anchor = GridBagConstraints.NORTH;
//		gbc_scheduler.gridx = 0;
//		gbc_scheduler.gridy = 0;
//		panel.add(scheduler, gbc_scheduler);

		IntakeWaterTemperaturePanel intake = new IntakeWaterTemperaturePanel();
		intake.setReference(server.getIntakeWaterTemperature());
		GridBagConstraints gbc_intake = new GridBagConstraints();
		gbc_intake.insets = new Insets(5, 10, 5, 10);
		gbc_intake.anchor = GridBagConstraints.NORTH;
		gbc_intake.gridx = 0;
		gbc_intake.gridy = 1;
		panel.add(intake, gbc_intake);

		tapPoint_1 = new RealTapPointUI(point1);
		panel.add(tapPoint_1, createOutletConstraints(1, 0));

		tapPoint_2 = new SimTapPointUI(point2);
		panel.add(tapPoint_2, createOutletConstraints(2, 0));

		tapPoint_3 = new SimTapPointUI(point3);
		panel.add(tapPoint_3, createOutletConstraints(1, 1));

		tapPoint_4 = new SimTapPointUI(point4);
		panel.add(tapPoint_4, createOutletConstraints(2, 1));

		getContentPane().add(panel);
	}

	private GridBagConstraints createOutletConstraints(int x, int y) {
		GridBagConstraints gbc_outlet_1 = new GridBagConstraints();
		gbc_outlet_1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet_1.fill = GridBagConstraints.BOTH;
		gbc_outlet_1.gridx = x;
		gbc_outlet_1.gridy = y;
		return gbc_outlet_1;
	}

	public static void main(String[] args) throws Exception {
		try {
			ElmLogFormatter.init();
		} catch (SecurityException | IOException e) {
			System.exit(1);
		}

		final TapPointImpl point1 = new TapPointImpl("2 OG lk - Dusche", POINT_1_ID, HotWaterTemperature.TEMP_2);
		final TapPointImpl point2 = new TapPointImpl("2 OG lk - Küche", POINT_2_ID, HotWaterTemperature.TEMP_2);
		final TapPointImpl point3 = new TapPointImpl("1 OG lk - Dusche", POINT_3_ID, HotWaterTemperature.TEMP_2);
		final TapPointImpl point4 = new TapPointImpl("1 OG lk - Küche", POINT_4_ID, HotWaterTemperature.TEMP_2);

		final SimHomeServerImpl server = new SimHomeServerImpl("http://chs.local:9090");
		server.addDevice(POINT_1_ID, (short) 380, point1);
		server.addDevice(POINT_2_ID, (short) 420, point2);
		server.addDevice(POINT_3_ID, (short) 450, point3);
		server.addDevice(POINT_4_ID, (short) 300, point4);

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					SimHomeServerApplicationUI ex = new SimHomeServerApplicationUI(server, point1, point2, point3, point4);
					ex.setVisible(true);
				} catch (UnsupportedModelException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
		});
		SimHomeServerServer httpServer = new SimHomeServerServer(server);
		httpServer.start();
		// try {
		// JmDNS jmDNS = JmDNS.create();
		// ServiceInfo info = ServiceInfo.create("_clage-hs._tcp.local.", "Home Server Sim", 9090, "Home Server Simulation");
		// jmDNS.registerService(info);
		// System.out.println(HomeServerSimulation.class.getSimpleName() + " registered as Bonjour service.");
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		System.out.println(SimHomeServerDemoServer.class.getSimpleName() + " started.");
	}

}
