package elm.sim.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.Scheduler;
import elm.sim.model.Status;
import elm.sim.model.impl.SchedulerImpl;

public class SchedulerMonitorUI extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(SchedulerMonitorUI.class.getName());

	@SuppressWarnings("serial")
	class StatusPanel extends EnumSelectorPanel<Status> {

		StatusPanel() {
			super("Status", Status.OFF, Status.ON, Status.SATURATION, Status.OVERLOAD, Status.ERROR);
		}

		@Override
		protected void selectionChanged(Status newValue) {
			info("Status changed: " + newValue.getLabel());
			model.setStatus(newValue);
		}
	}

	// State
	private final SchedulerImpl model;

	// Widgets
	private final StatusPanel statusPanel;
	private JTextField waitingTimeSeconds;
	private JTable outlets;;

	private final SimModelListener modelListener = new SimModelListener() {

		@Override
		public void modelChanged(final SimModelEvent event) {
			if (!model.equals(event.getSource())) {
				throw new IllegalArgumentException("Wrong event source: " + event.getSource().toString());
			}

			// Update widget's on Swing thread (invocation comes from scheduler on its own thread)
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					switch ((SchedulerImpl.Attribute) event.getAttribute()) {
					case STATUS:
						statusPanel.setSelection((Status) event.getNewValue());
						break;
					case WAITING_TIME_SECONDS:
						waitingTimeSeconds.setText(Integer.toString((int) event.getNewValue()));
						break;
					default:
						throw new IllegalArgumentException(event.getAttribute().id());
					}
				}
			});
		}
	};

	/**
	 * Create the panel.
	 */
	public SchedulerMonitorUI(SchedulerImpl model) {
		assert model != null;
		this.model = model;
		model.addModelListener(modelListener);

		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0 };
		gridBagLayout.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
		gridBagLayout.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0 };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
		setLayout(gridBagLayout);

		JLabel title = new JLabel("Scheduler");
		title.setFont(new Font("Lucida Grande", Font.PLAIN, 15));
		GridBagConstraints gbc_title = new GridBagConstraints();
		gbc_title.insets = new Insets(0, 0, 5, 0);
		gbc_title.gridx = 0;
		gbc_title.gridy = 0;
		add(title, gbc_title);

		// Status
		statusPanel = new StatusPanel();
		GridBagConstraints gbc_status = new GridBagConstraints();
		gbc_status.anchor = GridBagConstraints.NORTH;
		gbc_status.insets = new Insets(5, 5, 5, 0);
		gbc_status.gridx = 0;
		gbc_status.gridy = 1;
		add(statusPanel, gbc_status);

		// Wartezeit
		JLabel waitingTimeLabel = new JLabel("Wartezeit [s]");
		GridBagConstraints gbc_waitingTimeLabel = new GridBagConstraints();
		gbc_waitingTimeLabel.anchor = GridBagConstraints.WEST;
		gbc_waitingTimeLabel.insets = new Insets(0, 0, 5, 0);
		gbc_waitingTimeLabel.gridx = 0;
		gbc_waitingTimeLabel.gridy = 2;
		add(waitingTimeLabel, gbc_waitingTimeLabel);

		waitingTimeSeconds = new JTextField();
		waitingTimeLabel.setEnabled(false);
		GridBagConstraints gbc_waitingTimeSeconds = new GridBagConstraints();
		gbc_waitingTimeSeconds.insets = new Insets(0, 0, 5, 0);
		gbc_waitingTimeSeconds.fill = GridBagConstraints.HORIZONTAL;
		gbc_waitingTimeSeconds.gridx = 0;
		gbc_waitingTimeSeconds.gridy = 3;
		add(waitingTimeSeconds, gbc_waitingTimeSeconds);
		waitingTimeSeconds.setColumns(10);

		// Zapfstellen
		JLabel outletsLabel = new JLabel("Zapfstellen");
		GridBagConstraints gbc_outletsLabel = new GridBagConstraints();
		gbc_outletsLabel.anchor = GridBagConstraints.WEST;
		gbc_outletsLabel.insets = new Insets(0, 0, 5, 0);
		gbc_outletsLabel.gridx = 0;
		gbc_outletsLabel.gridy = 4;
		add(outletsLabel, gbc_outletsLabel);

		outlets = new JTable();
		outlets.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		GridBagConstraints gbc_outlets = new GridBagConstraints();
		gbc_outlets.fill = GridBagConstraints.BOTH;
		gbc_outlets.gridx = 0;
		gbc_outlets.gridy = 5;
		add(outlets, gbc_outlets);

		updateFromModel(model);
	}

	private void updateFromModel(Scheduler model) {
		statusPanel.setSelection(model.getStatus());
	}

	public SchedulerImpl getModel() {
		return model;
	}

	private void info(String msg) {
		LOG.info("Scheduler, " + msg);
	}

}
