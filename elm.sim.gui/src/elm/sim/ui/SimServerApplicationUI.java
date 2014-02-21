package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JPanel;

import elm.hs.api.sim.server.SimHomeServer;
import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.IntakeWaterTemperature;
import elm.sim.model.SimStatus;
import elm.sim.model.SimpleScheduler;
import elm.sim.model.TapPoint;
import elm.sim.model.impl.SimpleSchedulerImpl;

@SuppressWarnings("serial")
public class SimServerApplicationUI extends JFrame {

	class IntakeWaterTemperaturePanel extends EnumSelectorPanel<IntakeWaterTemperature> {

		IntakeWaterTemperaturePanel() {
			super("Kaltwasser", false, IntakeWaterTemperature.TEMP_5, IntakeWaterTemperature.TEMP_10, IntakeWaterTemperature.TEMP_15,
					IntakeWaterTemperature.TEMP_20);
		}

		@Override
		protected void referenceValueChanged(IntakeWaterTemperature newValue) {
			getServer().setIntakeWaterTemperature(newValue);
		}
	}

	private final AbstractSimServerApplicationConfiguration configuration;
	private final SimpleSchedulerUI schedulerUI;
	private final AbstractTapPointUI[][] pointUIs;

	/**
	 * @param configuration
	 *            cannot be {@code null}
	 */
	public SimServerApplicationUI(AbstractSimServerApplicationConfiguration configuration) {
		assert configuration != null;
		assert configuration.getServer() != null;
		assert configuration.getTapPoints() != null;
		this.configuration = configuration;

		// Set defaults; can be changed later before making the frame visible
		setTitle("Durchlauferhitzer-Simulation");
		setSize(700, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);

		if (configuration.getScheduler() != null) {
			schedulerUI = new SimpleSchedulerUI(configuration.getScheduler());
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
		intake.setReference(configuration.getServer().getIntakeWaterTemperature());
		GridBagConstraints gbc_intake = new GridBagConstraints();
		gbc_intake.insets = new Insets(5, 5, 5, 5);
		gbc_intake.anchor = GridBagConstraints.NORTH;
		gbc_intake.fill = GridBagConstraints.HORIZONTAL;
		gbc_intake.gridx = 0;
		gbc_intake.gridy = 1;
		panel.add(intake, gbc_intake);

		int maxRowLength = 0;
		final TapPoint[][] points = configuration.getTapPoints();
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
				panel.add(pointUIs[row][col], createOutletConstraints(col+1, row));
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
		return configuration.getServer();
	}

}
