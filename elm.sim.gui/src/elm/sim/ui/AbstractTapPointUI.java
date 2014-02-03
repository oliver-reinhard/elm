package elm.sim.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

import elm.sim.model.TapPoint;
import elm.sim.model.Status;

@SuppressWarnings("serial")
public abstract class AbstractTapPointUI extends JPanel {

	/** A blinker instance shared between all outlets. */
	protected static final LabelIconBlinker BLINKER = new LabelIconBlinker(SimulationUtil.getIcon(Status.ERROR), SimulationUtil.getIcon(Status.OFF));
	
	protected final TapPoint model;
	protected JLabel statusLabel;
	protected JProgressBar waitingTimePercent;

	public AbstractTapPointUI(final TapPoint model) {
		assert model != null;
		this.model = model;
		this.setName(model.getLabel());

		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		setLayout(createLayout());

		// Title
		JLabel title = new JLabel(model.getLabel());
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setFont(new Font("Lucida Grande", Font.PLAIN, 15));
		GridBagConstraints gbc_title = new GridBagConstraints();
		gbc_title.fill = GridBagConstraints.HORIZONTAL;
		gbc_title.gridwidth = 3;
		gbc_title.insets = new Insets(0, 0, 5, 0);
		gbc_title.gridx = 0;
		gbc_title.gridy = 0;
		add(title, gbc_title);
		
		addPanelContent();
		addStatusPanel(0, 2);
		updateFromModel(model);
	}
	
	protected GridBagLayout createLayout() {
		GridBagLayout gbl = new GridBagLayout();
		gbl.columnWidths = new int[] { 0, 0 };
		gbl.columnWeights = new double[] { 0.0, Double.MIN_VALUE };
		gbl.rowHeights = new int[] { 0, 0, 0 };
		gbl.rowWeights = new double[] { 0.0, 0.0, Double.MIN_VALUE };
		return gbl;
	}

	protected void addPanelContent() {
		// empty
	}

	protected void addStatusPanel(int gridx, int gridy) {
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
		gbc_status.gridwidth = 3;
		gbc_status.fill = GridBagConstraints.HORIZONTAL;
		gbc_status.gridx = gridx;
		gbc_status.gridy = gridy;
		add(statusPanel, gbc_status);

		statusLabel = new JLabel("Status");
		statusLabel.setHorizontalTextPosition(JLabel.LEFT);
		GridBagConstraints gbc_statusL = new GridBagConstraints();
		gbc_statusL.insets = new Insets(5, 10, 5, 5);
		gbc_statusL.gridx = 0;
		gbc_statusL.gridy = 0;
		statusPanel.add(statusLabel, gbc_statusL);

		waitingTimePercent = new JProgressBar();
		waitingTimePercent.setEnabled(false);
		GridBagConstraints gbc_progressBar = new GridBagConstraints();
		gbc_progressBar.insets = new Insets(0, 5, 0, 5);
		gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_progressBar.gridx = 1;
		gbc_progressBar.gridy = 0;
		statusPanel.add(waitingTimePercent, gbc_progressBar);
	}

	public TapPoint getModel() {
		return model;
	}

	protected void updateFromModel(TapPoint model) {
		setStatus(model.getStatus());
	}

	protected void setStatus(Status status) {
		assert status != null;
		if (status == Status.ERROR) {
			BLINKER.start(statusLabel);
		} else {
			BLINKER.stop(statusLabel);
			statusLabel.setIcon(SimulationUtil.getIcon(status));
		}
		waitingTimePercent.setEnabled(status == Status.OVERLOAD);
		waitingTimePercent.setValue(0);
	}

	protected void setWaitingTimePercent(int percent) {
		assert percent >= 0 && percent <= 100;
		waitingTimePercent.setValue(percent);
	}

}