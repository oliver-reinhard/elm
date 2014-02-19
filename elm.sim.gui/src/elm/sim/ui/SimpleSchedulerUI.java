package elm.sim.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.SimStatus;
import elm.sim.model.SimpleScheduler;
import elm.sim.model.impl.SimpleSchedulerImpl;

public class SimpleSchedulerUI extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(SimpleSchedulerUI.class.getName());

	@SuppressWarnings("serial")
	class StatusPanel extends EnumSelectorPanel<SimStatus> {

		StatusPanel() {
			super("Status", SimStatus.OFF, SimStatus.ON, SimStatus.SATURATION, SimStatus.OVERLOAD, SimStatus.ERROR);
		}

		@Override
		protected void referenceValueChanged(SimStatus newValue) {
			info("Status changed: " + newValue.getLabel());
			model.setStatus(newValue);
		}
	}

	// State
	private final SimpleScheduler model;

	// Widgets
	private final StatusPanel statusPanel;
	private JTextField waitingTimeSeconds;
	// private JTable outlets;;

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
					switch ((SimpleSchedulerImpl.Attribute) event.getAttribute()) {
					case STATUS:
						statusPanel.setReference((SimStatus) event.getNewValue());
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
	public SimpleSchedulerUI(SimpleScheduler model) {
		assert model != null;
		this.model = model;
		model.addModelListener(modelListener);

		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0 };
		gridBagLayout.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
		gridBagLayout.rowHeights = new int[] { 0, 0, 0, 0, 0 };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
		setLayout(gridBagLayout);

		JLabel title = new JLabel("Scheduler");
		title.setFont(new Font("Lucida Grande", Font.PLAIN, 15));
		GridBagConstraints gbc_title = new GridBagConstraints();
		gbc_title.insets = new Insets(0, 5, 5, 5);
		gbc_title.gridx = 0;
		gbc_title.gridy = 0;
		add(title, gbc_title);

		// Status
		statusPanel = new StatusPanel();
		GridBagConstraints gbc_status = new GridBagConstraints();
		gbc_status.anchor = GridBagConstraints.NORTH;
		gbc_status.insets = new Insets(5, 5, 5, 5);
		gbc_status.gridx = 0;
		gbc_status.gridy = 1;
		add(statusPanel, gbc_status);

		// Wartezeit
		JLabel waitingTimeLabel = new JLabel("Wartezeit [s]");
		GridBagConstraints gbc_waitingTimeLabel = new GridBagConstraints();
		gbc_waitingTimeLabel.anchor = GridBagConstraints.SOUTH;
		gbc_waitingTimeLabel.fill = GridBagConstraints.VERTICAL;
		gbc_waitingTimeLabel.insets = new Insets(0, 5, 0, 5);
		gbc_waitingTimeLabel.gridx = 0;
		gbc_waitingTimeLabel.gridy = 2;
		add(waitingTimeLabel, gbc_waitingTimeLabel);

		waitingTimeSeconds = new JTextField();
		waitingTimeSeconds.setEnabled(false);
		waitingTimeSeconds.setColumns(4);
		GridBagConstraints gbc_waitingTimeSeconds = new GridBagConstraints();
		gbc_waitingTimeSeconds.insets = new Insets(0, 5, 5, 5);
		gbc_waitingTimeSeconds.fill = GridBagConstraints.HORIZONTAL;
		gbc_waitingTimeSeconds.gridx = 0;
		gbc_waitingTimeSeconds.gridy = 3;
		add(waitingTimeSeconds, gbc_waitingTimeSeconds);

		updateFromModel(model);
	}

	private void updateFromModel(SimpleScheduler model) {
		statusPanel.setReference(model.getStatus());
	}

	public SimpleScheduler getModel() {
		return model;
	}

	private void info(String msg) {
		LOG.info("Scheduler, " + msg);
	}

}
