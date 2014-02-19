package elm.apps;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;

import elm.hs.api.sim.server.SimHomeServer;
import elm.hs.api.sim.server.SimHomeServerImpl;
import elm.scheduler.model.UnsupportedModelException;
import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.SimStatus;
import elm.sim.model.SimpleScheduler;
import elm.sim.model.TapPoint;
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
	private static final String POINT_4_ID = "2016FF0004";

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

	private final SimpleSchedulerUI schedulerUI;
	private final SimHomeServer server;
	private final AbstractTapPointUI[][] pointUIs;

	/**
	 * 
	 * @param server
	 *            cannot be {@code null}
	 * @param scheduler
	 *            can be {@code null} => no scheduler panel displayed
	 * @param points
	 *            cannot be {@code null}
	 * @throws UnsupportedModelException
	 *             if one of the tap points' id does not map to a known device model.
	 */
	public SimpleSimDemoApplicationUI(SimHomeServer server, SimpleScheduler scheduler, TapPoint[][] points) throws UnsupportedModelException {
		assert server != null;
		this.server = server;
		assert points != null;

		setTitle("Durchlauferhitzer-Simulation");
		setSize(700, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);

		if (scheduler != null) {
			schedulerUI = new SimpleSchedulerUI(scheduler);
			GridBagConstraints gbc_scheduler = new GridBagConstraints();
			gbc_scheduler.insets = new Insets(5, 5, 5, 5);
			gbc_scheduler.anchor = GridBagConstraints.NORTH;
			gbc_scheduler.fill = GridBagConstraints.VERTICAL;
			gbc_scheduler.gridx = 0;
			gbc_scheduler.gridy = 0;
			panel.add(schedulerUI, gbc_scheduler);
		} else {
			schedulerUI = null;
		}

		IntakeWaterTemperaturePanel intake = new IntakeWaterTemperaturePanel();
		intake.setReference(server.getIntakeWaterTemperature());
		GridBagConstraints gbc_intake = new GridBagConstraints();
		gbc_intake.insets = new Insets(5, 5, 5, 5);
		gbc_intake.anchor = GridBagConstraints.NORTH;
		gbc_intake.fill = GridBagConstraints.HORIZONTAL;
		gbc_intake.gridx = 0;
		gbc_intake.gridy = 1;
		panel.add(intake, gbc_intake);

		int maxRowLength = 0;
		for (int row = 0; row < points.length; row++) {
			if (points[row].length > maxRowLength) {
				maxRowLength = points[row].length;
			}
		}

		pointUIs = new AbstractTapPointUI[points.length][maxRowLength];
		for (int row = 0; row < points.length; row++) {
			for (int col = 0; col < points[row].length; col++) {
				TapPoint point = points[row][col];
				pointUIs[row][col] = point.isSimDevice() ? new SimTapPointUI(point) : new RealTapPointUI(point);
				panel.add(pointUIs[row][col], createOutletConstraints(row + 1, col));
			}
		}

		getContentPane().add(panel);

		if (schedulerUI != null) {
			schedulerUI.getModel().addModelListener(new SimModelListener() {

				@Override
				public void modelChanged(SimModelEvent e) {
					for (int row = 0; row < pointUIs.length; row++) {
						for (int col = 0; col < pointUIs[row].length; col++) {
							if (e.getAttribute() == SimpleScheduler.Attribute.STATUS) {
								pointUIs[row][col].getModel().setSchedulerStatus((SimStatus) e.getNewValue());
							} else if (e.getAttribute() == SimpleScheduler.Attribute.WAITING_TIME_SECONDS) {
								int waitTimePercent = (int) e.getNewValue() * 100 / SimpleSchedulerImpl.SIMULATED_WAITING_TIME_SECONDS;
								pointUIs[row][col].getModel().setWaitingTimePercent(waitTimePercent);
							}
						}
					}
				}
			});
		}
	}

	private GridBagConstraints createOutletConstraints(int x, int y) {
		GridBagConstraints gbc_outlet_1 = new GridBagConstraints();
		gbc_outlet_1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet_1.fill = GridBagConstraints.BOTH;
		gbc_outlet_1.gridx = x;
		gbc_outlet_1.gridy = y;
		return gbc_outlet_1;
	}

	public SimHomeServer getServer() {
		return server;
	}

	public static SimpleSimDemoApplicationUI createUI(final boolean showSimpleScheduler) throws UnsupportedModelException {
		try {
			ElmLogFormatter.init();
		} catch (SecurityException | IOException e) {
			System.exit(1);
		}
		final TapPoint point1 = new TapPointImpl("2 OG lk - Dusche", POINT_1_ID, false, HotWaterTemperature.TEMP_2); // "real" device
		final TapPoint point2 = new TapPointImpl("2 OG lk - Küche", POINT_2_ID, true, HotWaterTemperature.TEMP_2); // sim device
		final TapPoint point3 = new TapPointImpl("1 OG lk - Dusche", POINT_3_ID, true, HotWaterTemperature.TEMP_2); // sim device
		final TapPoint point4 = new TapPointImpl("1 OG lk - Küche", POINT_4_ID, true, HotWaterTemperature.TEMP_2); // sim device

		final TapPoint[][] points = new TapPoint[][] { { point1, point2 }, { point3, point4 } };

		final SimHomeServerImpl server = new SimHomeServerImpl("http://chs.local:9090");
		server.addDevice(POINT_1_ID, (short) 380, point1);
		server.addDevice(POINT_2_ID, (short) 420, point2);
		server.addDevice(POINT_3_ID, (short) 450, point3);
		server.addDevice(POINT_4_ID, (short) 300, point4);

		SimpleScheduler scheduler = showSimpleScheduler ? new SimpleSchedulerImpl() : null;

		final SimpleSimDemoApplicationUI ui = new SimpleSimDemoApplicationUI(server, scheduler, points);
		return ui;
	}

	public static void main(String[] args) throws UnsupportedModelException {
		createUI(true).setVisible(true);
	}

}
