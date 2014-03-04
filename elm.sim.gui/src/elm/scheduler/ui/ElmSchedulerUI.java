package elm.scheduler.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import elm.hs.api.model.ElmStatus;
import elm.scheduler.ElmScheduler;
import elm.scheduler.SchedulerChangeListener;

@SuppressWarnings("serial")
public class ElmSchedulerUI extends JFrame {

	private final JLabel status;
	private final JLabel totalPower;
	private final JLabel saturationLimit;
	private final JLabel overloadLimit;
	
	private Color statusBackground;

	/**
	 * @param scheduler
	 *            cannot be {@code null}
	 */
	public ElmSchedulerUI(final ElmScheduler scheduler) {
		assert scheduler != null;

		// Set defaults; can be changed later before making the frame visible
		setTitle("ELM Scheduler");
		setSize(400, 400);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);

		JLabel statusLabel = new JLabel("Status:");
		panel.add(statusLabel, createLabelConstraints(0, 0));

		status = new JLabel("");
		panel.add(status, createLabelConstraints(1, 0));
		statusBackground = status.getBackground();

		JLabel totalPowerLabel = new JLabel("Total requested power:");
		panel.add(totalPowerLabel, createLabelConstraints(0, 1));

		totalPower = new JLabel("");
		panel.add(totalPower, createLabelConstraints(1, 1));

		JLabel saturationLimitLabel = new JLabel("Saturation limit:");
		panel.add(saturationLimitLabel, createLabelConstraints(0, 2));

		saturationLimit = new JLabel(scheduler.formatPower(scheduler.getSaturationPowerLimitWatt()));
		panel.add(saturationLimit, createLabelConstraints(1, 2));

		JLabel overloadLimitLabel = new JLabel("Overload limit:");
		panel.add(overloadLimitLabel, createLabelConstraints(0, 3));

		overloadLimit = new JLabel(scheduler.formatPower(scheduler.getOverloadPowerLimitWatt()));
		panel.add(overloadLimit, createLabelConstraints(1, 3));

		scheduler.addChangeListener(new SchedulerChangeListener() {
			ElmStatus newStatus;
			int newPowerWatt;

			@Override
			public void statusChanged(ElmStatus oldStatus, ElmStatus newStatus) {
				this.newStatus = newStatus;
				updateUI();
			}

			@Override
			public void totalDemandPowerChanged(int oldPowerWatt, int newPowerWatt) {
				this.newPowerWatt = newPowerWatt;
				updateUI();
			}

			void updateUI() {
				// Update widget's on Swing thread (invocation comes from scheduler on its own thread)
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						status.setText(newStatus.name());
						if (newStatus == ElmStatus.ON) {
							status.setBackground(Color.GREEN);
						} else if (newStatus == ElmStatus.SATURATION) {
							status.setBackground(Color.YELLOW);
						} else if (newStatus.in(ElmStatus.OVERLOAD, ElmStatus.ERROR)) {
							status.setBackground(Color.RED);
						} else {
							status.setBackground(statusBackground);
						}
						
						totalPower.setText(scheduler.formatPower(newPowerWatt));
						if (newPowerWatt > 0) {
							totalPower.setForeground(Color.RED);
						} else {
							totalPower.setForeground(Color.BLACK);
						}
					}
				});
			}
		});

		getContentPane().add(panel);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				scheduler.stop();
				synchronized (this) {
					try {
						wait(2000);
					} catch (InterruptedException e1) {
						// ignore
					}
				}
				System.exit(0); // EXIT!
			}
		});
	}

	private GridBagConstraints createLabelConstraints(int x, int y) {
		GridBagConstraints gbc_outlet_1 = new GridBagConstraints();
		gbc_outlet_1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet_1.anchor = GridBagConstraints.WEST;
		gbc_outlet_1.gridx = x;
		gbc_outlet_1.gridy = y;
		return gbc_outlet_1;
	}

}
