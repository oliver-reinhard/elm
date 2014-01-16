package elm.sim.gui;

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
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

public class OutletUI extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(OutletUI.class.getName());

	/** A blinker instance shared between all outlets. */
	private static final LabelIconBlinker BLINKER = new LabelIconBlinker(SimulationUtil.getIcon(Status.ERROR), SimulationUtil.getIcon(Status.OFF));

	// State
	private Flow flow;
	private Temperature temperature;
	private boolean on;
	private Status outletStatus;
	private Status schedulerStatus;

	// Widgets
	private final JRadioButton flow1;
	private final JRadioButton flow2;
	private final JRadioButton flow3;

	private final JRadioButton temp1;
	private final JRadioButton temp2;
	private final JRadioButton temp3;
	private final JRadioButton temp4;

	private final JToggleButton onOff;

	private final JLabel status;
	private final JProgressBar waitingTimePercent;

	// Listeners
	private final ActionListener flowListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			Flow oldFlow = flow;
			if (src == flow1)
				flow = Flow.LOW;
			else if (src == flow2)
				flow = Flow.MEDIUM;
			else if (src == flow3)
				flow = Flow.HIGH;
			else
				throw new IllegalStateException();
			info("Flow changed: " + flow.getLabel());
			firePropertyChange(OutletModel.Properties.FLOW.id(), oldFlow, flow);
		}
	};

	private final ActionListener temperatureListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			Temperature oldTemp = temperature;
			if (src == temp1)
				temperature = Temperature.TEMP_1;
			else if (src == temp2)
				temperature = Temperature.TEMP_2;
			else if (src == temp3)
				temperature = Temperature.TEMP_3;
			else if (src == temp4)
				temperature = Temperature.TEMP_4;
			else
				throw new IllegalStateException();
			info("Temp changed: " + temperature.getLabel());
			firePropertyChange(OutletModel.Properties.TEMPERATURE.id(), oldTemp, temperature);
		}
	};

	private final ActionListener onOffListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			boolean oldOn = on;
			on = onOff.isSelected();
			updateOutletStatus(0);
			info("On/Off changed: " + (on ? "on" : "off"));
			firePropertyChange(OutletModel.Properties.ON.id(), oldOn, on);
		}
	};

	/**
	 * Create the panel.
	 */
	public OutletUI(String outletName) {
		assert outletName != null && !outletName.isEmpty();
		this.setName(outletName);

		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		GridBagLayout gbl = new GridBagLayout();
		gbl.columnWidths = new int[] { 0, 0, 0 };
		gbl.columnWeights = new double[] { 0.0, 0.0, Double.MIN_VALUE };
		gbl.rowHeights = new int[] { 0, 0, 0, 0, 0 };
		gbl.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		setLayout(gbl);

		// Title
		JLabel title = new JLabel(outletName);
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setFont(new Font("Lucida Grande", Font.PLAIN, 15));
		GridBagConstraints gbc_title = new GridBagConstraints();
		gbc_title.fill = GridBagConstraints.HORIZONTAL;
		gbc_title.gridwidth = 2;
		gbc_title.insets = new Insets(0, 0, 5, 0);
		gbc_title.gridx = 0;
		gbc_title.gridy = 0;
		add(title, gbc_title);

		// Flow
		JPanel flowPanel = new JPanel();
		GridBagLayout gbl_flow = new GridBagLayout();
		flowPanel.setLayout(gbl_flow);
		flowPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		GridBagConstraints gbc_flow = new GridBagConstraints();
		gbc_flow.anchor = GridBagConstraints.NORTH;
		gbc_flow.insets = new Insets(5, 5, 5, 5);
		gbc_flow.gridx = 0;
		gbc_flow.gridy = 1;
		add(flowPanel, gbc_flow);

		JLabel flow = new JLabel("Menge");
		GridBagConstraints gbc_flow0 = new GridBagConstraints();
		gbc_flow0.insets = new Insets(0, 0, 5, 5);
		gbc_flow0.gridx = 0;
		gbc_flow0.gridy = 0;
		flowPanel.add(flow, gbc_flow0);

		flow1 = new JRadioButton(Flow.LOW.getLabel());
		flow1.setFocusable(false);
		flow1.addActionListener(flowListener);
		GridBagConstraints gbc_flow1 = new GridBagConstraints();
		gbc_flow1.anchor = GridBagConstraints.WEST;
		gbc_flow1.insets = new Insets(0, 0, 5, 5);
		gbc_flow1.gridx = 0;
		gbc_flow1.gridy = 1;
		flowPanel.add(flow1, gbc_flow1);

		flow2 = new JRadioButton(Flow.MEDIUM.getLabel());
		flow2.setFocusable(false);
		flow2.addActionListener(flowListener);
		GridBagConstraints gbc_flow2 = new GridBagConstraints();
		gbc_flow2.anchor = GridBagConstraints.WEST;
		gbc_flow2.insets = new Insets(0, 0, 5, 5);
		gbc_flow2.gridx = 0;
		gbc_flow2.gridy = 2;
		flowPanel.add(flow2, gbc_flow2);

		flow3 = new JRadioButton(Flow.HIGH.getLabel());
		flow3.setFocusable(false);
		flow3.addActionListener(flowListener);
		GridBagConstraints gbc_flow3 = new GridBagConstraints();
		gbc_flow3.anchor = GridBagConstraints.WEST;
		gbc_flow3.insets = new Insets(0, 0, 5, 5);
		gbc_flow3.gridx = 0;
		gbc_flow3.gridy = 3;
		flowPanel.add(flow3, gbc_flow3);

		ButtonGroup flowGroup = new ButtonGroup();
		flowGroup.add(flow1);
		flowGroup.add(flow2);
		flowGroup.add(flow3);

		// Temperature
		JPanel tempPanel = new JPanel();
		GridBagLayout gbl_temp = new GridBagLayout();
		tempPanel.setLayout(gbl_temp);
		tempPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		GridBagConstraints gbc_temp = new GridBagConstraints();
		gbc_temp.anchor = GridBagConstraints.NORTH;
		gbc_temp.insets = new Insets(5, 5, 5, 5);
		gbc_temp.gridx = 1;
		gbc_temp.gridy = 1;
		add(tempPanel, gbc_temp);

		JLabel temp = new JLabel("Temperatur");
		GridBagConstraints gbc_temp0 = new GridBagConstraints();
		gbc_temp0.insets = new Insets(0, 5, 5, 5);
		gbc_temp0.gridx = 0;
		gbc_temp0.gridy = 0;
		tempPanel.add(temp, gbc_temp0);

		temp1 = new JRadioButton(Temperature.TEMP_1.getLabel());
		temp1.setFocusable(false);
		temp1.addActionListener(temperatureListener);
		GridBagConstraints gbc_temp1 = new GridBagConstraints();
		gbc_temp1.insets = new Insets(0, 0, 5, 0);
		gbc_temp1.gridx = 0;
		gbc_temp1.gridy = 1;
		tempPanel.add(temp1, gbc_temp1);

		temp2 = new JRadioButton(Temperature.TEMP_2.getLabel());
		temp2.setFocusable(false);
		temp2.addActionListener(temperatureListener);
		GridBagConstraints gbc_temp2 = new GridBagConstraints();
		gbc_temp2.insets = new Insets(0, 0, 5, 0);
		gbc_temp2.gridx = 0;
		gbc_temp2.gridy = 2;
		tempPanel.add(temp2, gbc_temp2);

		temp3 = new JRadioButton(Temperature.TEMP_3.getLabel());
		temp3.setFocusable(false);
		temp3.addActionListener(temperatureListener);
		GridBagConstraints gbc_temp3 = new GridBagConstraints();
		gbc_temp3.insets = new Insets(0, 0, 5, 0);
		gbc_temp3.gridx = 0;
		gbc_temp3.gridy = 3;
		tempPanel.add(temp3, gbc_temp3);

		temp4 = new JRadioButton(Temperature.TEMP_4.getLabel());
		temp4.setFocusable(false);
		temp4.addActionListener(temperatureListener);
		GridBagConstraints gbc_temp4 = new GridBagConstraints();
		gbc_temp4.insets = new Insets(0, 0, 5, 0);
		gbc_temp4.gridx = 0;
		gbc_temp4.gridy = 4;
		tempPanel.add(temp4, gbc_temp4);

		ButtonGroup tempGroup = new ButtonGroup();
		tempGroup.add(temp1);
		tempGroup.add(temp2);
		tempGroup.add(temp3);
		tempGroup.add(temp4);

		// On / Off
		onOff = new JToggleButton("Ein/Aus");
		onOff.addActionListener(onOffListener);
		GridBagConstraints gbc_onOff = new GridBagConstraints();
		gbc_onOff.fill = GridBagConstraints.HORIZONTAL;
		gbc_onOff.insets = new Insets(0, 0, 5, 0);
		gbc_onOff.gridwidth = 2;
		gbc_onOff.gridx = 0;
		gbc_onOff.gridy = 2;
		add(onOff, gbc_onOff);

		// Status
		JPanel statusPanel = new JPanel();
		GridBagLayout gbl_status = new GridBagLayout();
		gbl_status.columnWidths = new int[] { 0, 0 };
		gbl_status.columnWeights = new double[] { 0.0, Double.MIN_VALUE };
		statusPanel.setLayout(gbl_status);
		statusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		GridBagConstraints gbc_status = new GridBagConstraints();
		gbc_status.anchor = GridBagConstraints.NORTH;
		gbc_status.insets = new Insets(5, 5, 5, 5);
		gbc_status.gridwidth = 2;
		gbc_status.fill = GridBagConstraints.HORIZONTAL;
		gbc_status.gridx = 0;
		gbc_status.gridy = 3;
		add(statusPanel, gbc_status);

		status = new JLabel("Status");
		status.setHorizontalTextPosition(JLabel.LEFT);
		GridBagConstraints gbc_statusL = new GridBagConstraints();
		gbc_statusL.insets = new Insets(5, 10, 5, 5);
		gbc_statusL.gridx = 0;
		gbc_statusL.gridy = 0;
		statusPanel.add(status, gbc_statusL);

		waitingTimePercent = new JProgressBar();
		GridBagConstraints gbc_progressBar = new GridBagConstraints();
		gbc_progressBar.insets = new Insets(0, 5, 0, 5);
		gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_progressBar.gridx = 1;
		gbc_progressBar.gridy = 0;
		statusPanel.add(waitingTimePercent, gbc_progressBar);

		setEnabled(false);
		setFocusable(false);
	}

	public void setModel(OutletModel model) {
		if (model != null) {
			setFlow(model.getFlow());
			setTemperature(model.getTemperature());
			setOn(model.isOn());
			setEnabled(true);
		} else {
			setOn(false);
			setEnabled(false);
		}
	}

	public OutletModel getModel() {
		return new OutletModel(getName(), flow, temperature, on);
	}

	public void setModel(SchedulerModel model) {
		if (model != null) {
			this.schedulerStatus = model.getStatus();
			updateOutletStatus(model.getWaitingTimePercent());
		} else {
			BLINKER.stop(status);
			this.waitingTimePercent.setEnabled(false);
		}
	}

	private void updateOutletStatus(int waitingTimePercent) {
		this.waitingTimePercent.setEnabled(false);
		BLINKER.stop(status);
		if (schedulerStatus == Status.ERROR) {
			outletStatus = Status.ERROR;
			BLINKER.start(status);
		} else {
			if (this.on) {
				outletStatus = Status.ON;
			} else	if (schedulerStatus == Status.OVERLOAD) {
				outletStatus = Status.OVERLOAD;
				this.waitingTimePercent.setEnabled(true);
				this.waitingTimePercent.setValue(waitingTimePercent);
			} else if (schedulerStatus == Status.OFF) {
				outletStatus = Status.OFF;
			} else {
				outletStatus = schedulerStatus;
			}
			status.setIcon(SimulationUtil.getIcon(outletStatus));
			updatedOnOffEnablement();
		}
	}
	
	private void updatedOnOffEnablement() {
		onOff.setEnabled(outletStatus == Status.ON || outletStatus == Status.SATURATION);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		flow1.setEnabled(enabled);
		flow2.setEnabled(enabled);
		flow3.setEnabled(enabled);
		temp1.setEnabled(enabled);
		temp2.setEnabled(enabled);
		temp3.setEnabled(enabled);
		temp4.setEnabled(enabled);
		onOff.setEnabled(enabled);
		waitingTimePercent.setEnabled(enabled);
	}

	private void info(String msg) {
		LOG.info("Outlet " + getName() + ", " + msg);
	}

	private void setFlow(Flow flow) {
		this.flow = flow;
		switch (flow) {
		case LOW:
			flow1.setSelected(true);
			break;
		case MEDIUM:
			flow2.setSelected(true);
			break;
		case HIGH:
			flow3.setSelected(true);
			break;
		default:
			throw new IllegalArgumentException();
		}
	}

	private void setTemperature(Temperature temperature) {
		this.temperature = temperature;
		switch (temperature) {
		case TEMP_1:
			temp1.setSelected(true);
			break;
		case TEMP_2:
			temp2.setSelected(true);
			break;
		case TEMP_3:
			temp3.setSelected(true);
			break;
		case TEMP_4:
			temp4.setSelected(true);
			break;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	private void setOn(boolean on) {
		this.on = on;
		onOff.setSelected(on);
	}
}
