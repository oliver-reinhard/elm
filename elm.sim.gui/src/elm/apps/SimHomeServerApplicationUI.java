package elm.apps;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import elm.hs.api.sim.server.SimHomeServer;
import elm.hs.api.sim.server.SimHomeServerServer;
import elm.sim.model.Temperature;
import elm.sim.model.impl.SimpleSchedulerImpl;
import elm.sim.model.impl.TapPointImpl;
import elm.sim.ui.AbstractTapPointUI;
import elm.sim.ui.RealTapPointUI;
import elm.sim.ui.SimTapPointUI;
import elm.sim.ui.SimpleSchedulerUI;
import elm.util.ElmLogFormatter;

@SuppressWarnings("serial")
public class SimHomeServerApplicationUI extends JFrame {
	
	private AbstractTapPointUI tapPoint_1;
	private AbstractTapPointUI tapPoint_2;
	private AbstractTapPointUI tapPoint_3;
	private AbstractTapPointUI tapPoint_4;
	private SimpleSchedulerUI scheduler;

	public SimHomeServerApplicationUI(TapPointImpl point1, TapPointImpl point2, TapPointImpl point3, TapPointImpl point4) {
		setTitle("Dusche");
		setSize(800, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);
		
		
		scheduler = new SimpleSchedulerUI(new SimpleSchedulerImpl());
		GridBagConstraints gbc_scheduler = new GridBagConstraints();
		gbc_scheduler.insets = new Insets(5, 5, 5, 5);
		gbc_scheduler.gridheight = 2;
		gbc_scheduler.gridx = 0;
		gbc_scheduler.gridy = 0;
		panel.add(scheduler, gbc_scheduler);

		tapPoint_1 = new RealTapPointUI(point1);
		panel.add(tapPoint_1, createOutletConstraints(1,0));
		
		tapPoint_2 = new SimTapPointUI(point2);
		panel.add(tapPoint_2, createOutletConstraints(2,0));

		tapPoint_3 = new SimTapPointUI(point3);
		panel.add(tapPoint_3, createOutletConstraints(1,1));

		tapPoint_4 = new SimTapPointUI(point4);
		panel.add(tapPoint_4, createOutletConstraints(2,1));
		
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
		
		final TapPointImpl point1 = new TapPointImpl("2 OG lk - Dusche", Temperature.TEMP_2);
		final TapPointImpl point2 = new TapPointImpl("2 OG lk - Küche", Temperature.TEMP_2);
		final TapPointImpl point3 = new TapPointImpl("1 OG lk - Dusche", Temperature.TEMP_2);
		final TapPointImpl point4 = new TapPointImpl("1 OG lk - Küche", Temperature.TEMP_2);
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				SimHomeServerApplicationUI ex = new SimHomeServerApplicationUI(point1, point2, point3, point4);
				ex.setVisible(true);
			}
		});
		
		SimHomeServer db = new SimHomeServer("http://chs.local:9090");
		db.addDevice("A001FFFF33", (short) 380, point1);
		db.addDevice("A001FFFF66", (short) 420, point2);
		db.addDevice("6003FFFF1A", (short) 450, point3);
		db.addDevice("2016FFFF55", (short) 300, point4);
		SimHomeServerServer server = new SimHomeServerServer(db);
		server.start();
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
