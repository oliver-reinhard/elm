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
import elm.scheduler.model.UnsupportedModelException;
import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.SimStatus;
import elm.sim.model.SimpleScheduler;
import elm.sim.model.impl.SimpleSchedulerImpl;
import elm.sim.model.impl.TapPointImpl;
import elm.sim.ui.AbstractTapPointUI;
import elm.sim.ui.EnumSelectorPanel;
import elm.sim.ui.RealTapPointUI;
import elm.sim.ui.SimTapPointUI;
import elm.sim.ui.SimpleSchedulerUI;
import elm.util.ElmLogFormatter;

@SuppressWarnings("serial")
public class SimpleSimDemoApplicationUI extends JFrame {

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

	private SimpleSchedulerUI scheduler;
	private SimHomeServer server;
	private AbstractTapPointUI tapPoint_1;
	private AbstractTapPointUI tapPoint_2;
	private AbstractTapPointUI tapPoint_3;
	private AbstractTapPointUI tapPoint_4;

	public SimpleSimDemoApplicationUI(SimHomeServer server, TapPointImpl point1, TapPointImpl point2, TapPointImpl point3, TapPointImpl point4)
			throws UnsupportedModelException {
		this.server = server;
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
		gbc_scheduler.anchor = GridBagConstraints.NORTH;
		gbc_scheduler.gridx = 0;
		gbc_scheduler.gridy = 0;
		panel.add(scheduler, gbc_scheduler);

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

		// outlet_1.getModel().addModelListener(new SimModelListener() {
		//
		// @Override
		// public void modelChanged(SimModelEvent e) {
		// System.out.println("PCL: source = " + e.getSource());
		// System.out.println("PCL: value = " + e.getAttribute().id() + ": " + e.getOldValue() + " --> " + e.getNewValue());
		// }
		// });

		scheduler.getModel().addModelListener(new SimModelListener() {

			@Override
			public void modelChanged(SimModelEvent e) {
				// System.out.println("PCL: source = " + e.getSource());
				// System.out.println("PCL: value = " + e.getAttribute().id() + ": " + e.getOldValue() + " --> " + e.getNewValue());

				if (e.getAttribute() == SimpleScheduler.Attribute.STATUS) {
					tapPoint_1.getModel().setSchedulerStatus((SimStatus) e.getNewValue());
					tapPoint_2.getModel().setSchedulerStatus((SimStatus) e.getNewValue());
					tapPoint_3.getModel().setSchedulerStatus((SimStatus) e.getNewValue());
					tapPoint_4.getModel().setSchedulerStatus((SimStatus) e.getNewValue());
					// if (Status.OVERLOAD == e.getNewValue()) {
					// outlet_1.getModel().setWaitingTimePercent(25);
					// outlet_2.getModel().setWaitingTimePercent(25);
					// }
				} else if (e.getAttribute() == SimpleScheduler.Attribute.WAITING_TIME_SECONDS) {
					int waitTimePercent = (int) e.getNewValue() * 100 / SimpleSchedulerImpl.SIMULATED_WAITING_TIME_SECONDS;
					tapPoint_1.getModel().setWaitingTimePercent(waitTimePercent);
					tapPoint_2.getModel().setWaitingTimePercent(waitTimePercent);
					tapPoint_3.getModel().setWaitingTimePercent(waitTimePercent);
					tapPoint_4.getModel().setWaitingTimePercent(waitTimePercent);
				}
			}
		});
	}

	private GridBagConstraints createOutletConstraints(int x, int y) {
		GridBagConstraints gbc_outlet_1 = new GridBagConstraints();
		gbc_outlet_1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet_1.fill = GridBagConstraints.BOTH;
		gbc_outlet_1.gridx = x;
		gbc_outlet_1.gridy = y;
		return gbc_outlet_1;
	}

	public static void main(String[] args) throws UnsupportedModelException {
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
					SimpleSimDemoApplicationUI ex = new SimpleSimDemoApplicationUI(server, point1, point2, point3, point4);
					ex.setVisible(true);
				} catch (UnsupportedModelException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
		});
	}

}
