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

	class ConsumptionTimeUpdater implements Runnable {
		private Thread running;
		private boolean shouldStop;

		public synchronized void start() {
			if (running == null) {
				running = new Thread(this, "Time Updater");
				running.setDaemon(true); // will not prevent process from exiting
				shouldStop = false;
				running.start();
			}
		}

		public synchronized void stop() {
			if (running != null) {
				shouldStop = true;
				this.notify();
				running = null;
			}
		}

		@Override
		public synchronized void run() {
			try {
				while (!shouldStop) {
					long time = System.currentTimeMillis();
					for (int row = 0; row < pointUIs.length; row++) {
						for (int col = 0; col < pointUIs[row].length; col++) {
							pointUIs[row][col].updateConsumptionDuration(time);
						}
					}
					this.wait(1000);
					if (shouldStop) {
						break;
					}
					this.wait(500);
				}
			} catch (InterruptedException e) {
				// exited the loop, goal reached
			}
			running = null;
		}
	}

	private final AbstractSimServerApplicationConfiguration configuration;
	private final SimpleSchedulerUI simpleSchedulerUI;
	private final AbstractTapPointUI[][] pointUIs;
	private final ConsumptionTimeUpdater timeUpdater = new ConsumptionTimeUpdater();

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
		setSize(750, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);

		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);

		if (configuration.getScheduler() != null) {
			simpleSchedulerUI = new SimpleSchedulerUI(configuration.getScheduler());
			GridBagConstraints gbc_scheduler = new GridBagConstraints();
			gbc_scheduler.insets = new Insets(5, 5, 5, 5);
			gbc_scheduler.anchor = GridBagConstraints.NORTH;
			gbc_scheduler.fill = GridBagConstraints.VERTICAL;
			gbc_scheduler.gridx = 0;
			gbc_scheduler.gridy = 0;
			panel.add(simpleSchedulerUI, gbc_scheduler);
		} else {
			simpleSchedulerUI = null;
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
				panel.add(pointUIs[row][col], createOutletConstraints(col + 1, row));
			}
		}

		getContentPane().add(panel);

		// Do this only if we use the SimpleScheduler that allows manual setting of the ELM status
		if (simpleSchedulerUI != null) {
			simpleSchedulerUI.getModel().addModelListener(new SimModelListener() {

				@Override
				public void modelChanged(SimModelEvent e) {
					for (int row = 0; row < pointUIs.length; row++) {
						for (int col = 0; col < pointUIs[row].length; col++) {
							if (e.getAttribute() == SimpleScheduler.Attribute.STATUS) {
								pointUIs[row][col].getModel().setSchedulerStatus((SimStatus) e.getNewValue());
							} else if (e.getAttribute() == SimpleScheduler.Attribute.WAITING_TIME_SECONDS) {
								pointUIs[row][col].getModel().setWaitingTimeMillis(((int) e.getNewValue()) * SimpleScheduler.MILLIS_PER_SECOND);
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
	
	public void start() {
		timeUpdater.start();
	}
	
	public void stop() {
		timeUpdater.stop();
	}

}
