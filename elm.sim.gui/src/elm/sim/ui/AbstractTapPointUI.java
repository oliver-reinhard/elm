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
import elm.sim.model.SimStatus;

@SuppressWarnings("serial")
public abstract class AbstractTapPointUI extends JPanel {

	private static final int MILLIS_PER_SECOND = 1000;

	/** A blinker instance shared between all outlets. */
	protected static final LabelIconBlinker BLINKER = new LabelIconBlinker(SimUtil.getIcon(SimStatus.ERROR), SimUtil.getIcon(SimStatus.OFF));

	protected final TapPoint model;
	protected JLabel statusLabel;
	protected JProgressBar waitingTimeBar;

	protected JLabel id;

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
		addStatusPanel(0, 3);
		updateFromModel();
	}

	protected GridBagLayout createLayout() {
		GridBagLayout gbl = new GridBagLayout();
		gbl.columnWidths = new int[] { 0, 0 };
		gbl.columnWeights = new double[] { 0.0, Double.MIN_VALUE };
		gbl.rowHeights = new int[] { 0, 0, 0, 0 };
		gbl.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
		return gbl;
	}

	protected void addPanelContent() {
		id = new JLabel("ID: " + model.getId() + " (" + model.getDeviceModel().name() + ")");
		add(id, createLabelConstraints(0, 1));
	}

	protected void addStatusPanel(int gridx, int gridy) {
		// Status
		JPanel statusPanel = new JPanel();
		GridBagLayout gbl_status = new GridBagLayout();
		gbl_status.columnWidths = new int[] { 0, 0 };
		gbl_status.columnWeights = new double[] { 0.0, 0.0, Double.MIN_VALUE };
		statusPanel.setLayout(gbl_status);
		statusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		GridBagConstraints gbc_status = new GridBagConstraints();
		gbc_status.anchor = GridBagConstraints.SOUTH;
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

		waitingTimeBar = new JProgressBar();
		waitingTimeBar.setEnabled(false);
		GridBagConstraints gbc_progressBar = new GridBagConstraints();
		gbc_progressBar.insets = new Insets(0, 5, 5, 5);
		gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_progressBar.gridx = 1;
		gbc_progressBar.gridy = 0;
		statusPanel.add(waitingTimeBar, gbc_progressBar);
	}

	protected GridBagConstraints createLabelConstraints(int x, int y) {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(0, 5, 0, 5);
		gbc.gridx = x;
		gbc.gridy = y;
		return gbc;
	}

	public TapPoint getModel() {
		return model;
	}

	protected void updateFromModel() {
		setStatus(model.getStatus());
	}

	protected void setStatus(SimStatus status) {
		assert status != null;
		if (status == SimStatus.ERROR) {
			BLINKER.start(statusLabel);
		} else {
			BLINKER.stop(statusLabel);
			statusLabel.setIcon(SimUtil.getIcon(status));
		}
		waitingTimeBar.setEnabled(status == SimStatus.OVERLOAD);
		waitingTimeBar.setStringPainted(status == SimStatus.OVERLOAD);
		waitingTimeBar.setValue(0);
	}

	protected void setWaitingTimeMillis(int value) {
		assert value >= 0;
		int seconds = (value + 500) / MILLIS_PER_SECOND; // round up
		if (seconds > 0) {
			int timeRounded = 0;
			String timeUnit = "";
			int percent = 0;
			if (seconds > 300) { // > 5 minutes => 100 %
				percent = 100;
				timeRounded = ((seconds + 30) / 60);// round up
				timeUnit = "Minute";
			} else if (seconds > 60) { // 5 min .. 60 sec => top 50 .. 100 %
				percent = 50 + 50 * (seconds - 60) / (300 - 60);
				timeRounded = ((seconds + 30) / 60); // round up
				timeUnit = "Minute";
			} else if (seconds > 10) { // 60 .. 10 sec => 25 .. 50 %
				percent = 25 + 25 * (seconds - 10) / (60 - 20);
				timeRounded = (((seconds + 9) / 10) * 10); // round up
				timeUnit = "Sekunde";
			} else if (seconds > 0) { // => 0 .. 25 %
				percent = 25 * seconds / 10;
				timeRounded = (((seconds + 9) / 10) * 10); // round up
				timeUnit = "Sekunde";
			}
			waitingTimeBar.setValue(percent);
			waitingTimeBar.setString("Ca. " + timeRounded + " " + timeUnit + (timeRounded > 1 ? "n" : ""));
		} else {
			waitingTimeBar.setValue(2);
			waitingTimeBar.setString("Zapfungsende abwarten");
		}
	}

}