package elm.sim.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.Scheduler;
import elm.sim.model.Status;

public class SchedulerMonitorUI extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(SchedulerMonitorUI.class.getName());

	// State
	private final Scheduler model;

	// Widgets
	private final JRadioButton statusOff;
	private final JRadioButton statusOn;
	private final JRadioButton statusSaturation;
	private final JRadioButton statusOverload;
	private final JRadioButton statusError;

	private JTextField waitingTimeSeconds;

	private JTable outlets;;

	// Listeners
	private final ActionListener statusListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			Status status;
			if (src == statusOff)
				status = Status.OFF;
			else if (src == statusOn)
				status = Status.ON;
			else if (src == statusSaturation)
				status = Status.SATURATION;
			else if (src == statusOverload)
				status = Status.OVERLOAD;
			else if (src == statusError)
				status = Status.ERROR;
			else
				throw new IllegalStateException();
			info("Status changed: " + status.getLabel());
			model.setStatus(status);
		}
	};

	private final SimModelListener modelListener = new SimModelListener() {

		@Override
		public void modelChanged(SimModelEvent e) {
			if (!model.equals(e.getSource())) {
				throw new IllegalArgumentException("Wrong event source: " + e.getSource().toString());
			}

			switch ((Scheduler.Attribute) e.getAttribute()) {
			case STATUS:
				break;
			case WAITING_TIME_SECONDS:
				break;
			default:
				throw new IllegalArgumentException(e.getAttribute().id());
			}
		}
	};

	/**
	 * Create the panel.
	 */
	public SchedulerMonitorUI(Scheduler model) {
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
		JPanel statusPanel = new JPanel();
		GridBagLayout gbl_status = new GridBagLayout();
		statusPanel.setLayout(gbl_status);
		statusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		GridBagConstraints gbc_status = new GridBagConstraints();
		gbc_status.anchor = GridBagConstraints.NORTH;
		gbc_status.insets = new Insets(5, 5, 5, 0);
		gbc_status.gridx = 0;
		gbc_status.gridy = 1;
		add(statusPanel, gbc_status);

		JLabel status = new JLabel("Status");
		GridBagConstraints gbc_status0 = new GridBagConstraints();
		gbc_status0.anchor = GridBagConstraints.WEST;
		gbc_status0.insets = new Insets(0, 5, 5, 5);
		gbc_status0.gridx = 0;
		gbc_status0.gridy = 0;
		statusPanel.add(status, gbc_status0);

		statusOff = new JRadioButton(Status.OFF.getLabel());
		statusOff.setFocusable(false);
		statusOff.addActionListener(statusListener);
		GridBagConstraints gbc_status1 = new GridBagConstraints();
		gbc_status1.anchor = GridBagConstraints.WEST;
		gbc_status1.insets = new Insets(0, 0, 5, 0);
		gbc_status1.gridx = 0;
		gbc_status1.gridy = 1;
		statusPanel.add(statusOff, gbc_status1);

		statusOn = new JRadioButton(Status.ON.getLabel());
		statusOn.setFocusable(false);
		statusOn.addActionListener(statusListener);
		GridBagConstraints gbc_status2 = new GridBagConstraints();
		gbc_status2.anchor = GridBagConstraints.WEST;
		gbc_status2.insets = new Insets(0, 0, 5, 0);
		gbc_status2.gridx = 0;
		gbc_status2.gridy = 2;
		statusPanel.add(statusOn, gbc_status2);

		statusSaturation = new JRadioButton(Status.SATURATION.getLabel());
		statusSaturation.setFocusable(false);
		statusSaturation.addActionListener(statusListener);
		GridBagConstraints gbc_status3 = new GridBagConstraints();
		gbc_status3.anchor = GridBagConstraints.WEST;
		gbc_status3.insets = new Insets(0, 0, 5, 0);
		gbc_status3.gridx = 0;
		gbc_status3.gridy = 3;
		statusPanel.add(statusSaturation, gbc_status3);

		statusOverload = new JRadioButton(Status.OVERLOAD.getLabel());
		statusOverload.setFocusable(false);
		statusOverload.addActionListener(statusListener);
		GridBagConstraints gbc_status4 = new GridBagConstraints();
		gbc_status4.anchor = GridBagConstraints.WEST;
		gbc_status4.insets = new Insets(0, 0, 5, 0);
		gbc_status4.gridx = 0;
		gbc_status4.gridy = 4;
		statusPanel.add(statusOverload, gbc_status4);

		statusError = new JRadioButton(Status.ERROR.getLabel());
		statusError.setFocusable(false);
		statusError.addActionListener(statusListener);
		GridBagConstraints gbc_status5 = new GridBagConstraints();
		gbc_status5.anchor = GridBagConstraints.WEST;
		gbc_status5.insets = new Insets(0, 0, 5, 0);
		gbc_status5.gridx = 0;
		gbc_status5.gridy = 5;
		statusPanel.add(statusError, gbc_status5);

		ButtonGroup statusGroup = new ButtonGroup();
		statusGroup.add(statusOff);
		statusGroup.add(statusOn);
		statusGroup.add(statusSaturation);
		statusGroup.add(statusOverload);
		statusGroup.add(statusError);

		JLabel waitingTimeLabel = new JLabel("Wartezeit [s]");
		GridBagConstraints gbc_waitingTimeLabel = new GridBagConstraints();
		gbc_waitingTimeLabel.anchor = GridBagConstraints.WEST;
		gbc_waitingTimeLabel.insets = new Insets(0, 0, 5, 0);
		gbc_waitingTimeLabel.gridx = 0;
		gbc_waitingTimeLabel.gridy = 2;
		add(waitingTimeLabel, gbc_waitingTimeLabel);

		waitingTimeSeconds = new JTextField();
		GridBagConstraints gbc_waitingTimeSeconds = new GridBagConstraints();
		gbc_waitingTimeSeconds.insets = new Insets(0, 0, 5, 0);
		gbc_waitingTimeSeconds.fill = GridBagConstraints.HORIZONTAL;
		gbc_waitingTimeSeconds.gridx = 0;
		gbc_waitingTimeSeconds.gridy = 3;
		add(waitingTimeSeconds, gbc_waitingTimeSeconds);
		waitingTimeSeconds.setColumns(10);

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
		setStatus(model.getStatus());
	}

	public Scheduler getModel() {
		return model;
	}

	private void setStatus(Status status) {
		assert status != null;
		switch (status) {
		case ON:
			statusOn.setSelected(true);
			break;
		case OFF:
			statusOff.setSelected(true);
			break;
		case SATURATION:
			statusSaturation.setSelected(true);
			break;
		case OVERLOAD:
			statusOverload.setSelected(true);
			break;
		case ERROR:
			statusError.setSelected(true);
			break;
		default:
			throw new IllegalArgumentException(status.toString());
		}
	}

	private void info(String msg) {
		LOG.info("Scheduler, " + msg);
	}

}
