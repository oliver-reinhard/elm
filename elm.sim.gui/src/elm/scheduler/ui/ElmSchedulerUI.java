package elm.scheduler.ui;

import static elm.util.ElmLogFormatter.formatPower;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import elm.hs.api.ElmStatus;
import elm.scheduler.ElmScheduler;
import elm.scheduler.ElmSchedulerChangeListener;

@SuppressWarnings("serial")
public class ElmSchedulerUI extends JFrame {

	private static final Color GREEN = new Color(0, 128, 0);
	
	private final JTextField status;
	private final JTextField totalPower;
	private final JLabel saturationLimit;
	private final JLabel overloadLimit;

	/**
	 * @param scheduler
	 *            cannot be {@code null}
	 */
	public ElmSchedulerUI(final ElmScheduler scheduler) {
		assert scheduler != null;

		// Set defaults; can be changed later before making the frame visible
		setTitle("ELM-Scheduler");
		setSize(300, 200);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		gbl.columnWidths = new int[] { 0, 0, };
		gbl.columnWeights = new double[] { 0.0, Double.MIN_VALUE };
		panel.setLayout(gbl);

		JLabel statusLabel = new JLabel("Status:");
		panel.add(statusLabel, createLabelConstraints(0, 0));

		status = new JTextField("");
		status.setEditable(false);
		GridBagConstraints gbc_status = createLabelConstraints(1, 0);
		gbc_status.fill = GridBagConstraints.HORIZONTAL;
		panel.add(status, gbc_status);

		JLabel totalPowerLabel = new JLabel("Total benötigte Leistung:");
		panel.add(totalPowerLabel, createLabelConstraints(0, 1));

		totalPower = new JTextField("");
		totalPower.setEditable(false);
		GridBagConstraints gbc_power = createLabelConstraints(1, 1);
		gbc_power.fill = GridBagConstraints.HORIZONTAL;
		panel.add(totalPower, gbc_power);

		JLabel saturationLimitLabel = new JLabel("Sättigungsgrenze:");
		panel.add(saturationLimitLabel, createLabelConstraints(0, 2));

		saturationLimit = new JLabel(formatPower(scheduler.getSaturationPowerLimitWatt()));
		panel.add(saturationLimit, createLabelConstraints(1, 2));

		JLabel overloadLimitLabel = new JLabel("Überlastgrenze:");
		panel.add(overloadLimitLabel, createLabelConstraints(0, 3));

		overloadLimit = new JLabel(formatPower(scheduler.getOverloadPowerLimitWatt()));
		panel.add(overloadLimit, createLabelConstraints(1, 3));

		scheduler.addChangeListener(new ElmSchedulerChangeListener() {
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
						status.setText(newStatus.getLabel());
						if (newStatus == ElmStatus.ON) {
							status.setForeground(GREEN);
						} else if (newStatus == ElmStatus.SATURATION) {
							status.setForeground(Color.ORANGE);
						} else if (newStatus.in(ElmStatus.OVERLOAD, ElmStatus.ERROR)) {
							status.setForeground(Color.RED);
						} else {
							status.setForeground(Color.BLACK);
						}
						
						totalPower.setText(formatPower(newPowerWatt));
						if (newPowerWatt > scheduler.getOverloadPowerLimitWatt()) {
							totalPower.setForeground(Color.RED);
						} else if (newPowerWatt > scheduler.getSaturationPowerLimitWatt()) {
							totalPower.setForeground(Color.ORANGE);
						} else {
							totalPower.setForeground(GREEN);
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
