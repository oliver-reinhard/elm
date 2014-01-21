package elm.sim.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.DemandEnablement;
import elm.sim.model.Flow;
import elm.sim.model.Outlet;
import elm.sim.model.Status;
import elm.sim.model.Temperature;
import elm.sim.model.impl.OutletImpl;

public class OutletUI extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(OutletUI.class.getName());

	/** A blinker instance shared between all outlets. */
	private static final LabelIconBlinker BLINKER = new LabelIconBlinker(SimulationUtil.getIcon(Status.ERROR), SimulationUtil.getIcon(Status.OFF));

	@SuppressWarnings("serial")
	class DemandFlow extends EnumSelectorPanel<Flow> {

		DemandFlow() {
			super("Soll-Menge", Flow.NONE, Flow.LOW, Flow.MEDIUM, Flow.HIGH);
		}

		@Override
		protected void selectionChanged(Flow newValue) {
			info("Demand flow changed: " + newValue.getLabel());
			model.setDemandFlow(newValue);
		}
	}

	@SuppressWarnings("serial")
	class ActualFlow extends EnumSelectorPanel<Flow> {

		ActualFlow() {
			super("Ist-Menge", Flow.NONE, Flow.LOW, Flow.MEDIUM, Flow.HIGH);
		}

		@Override
		protected void selectionChanged(Flow newValue) {
			info("Actual flow changed: " + newValue.getLabel());
			model.setActualFlow(newValue);
		}
	}

	@SuppressWarnings("serial")
	class DemandTemperature extends EnumSelectorPanel<Temperature> {

		DemandTemperature() {
			super("Soll-Temperatur", Temperature.TEMP_1, Temperature.TEMP_2, Temperature.TEMP_3, Temperature.TEMP_4);
		}

		@Override
		protected void selectionChanged(Temperature newValue) {
			info("Demand temperature changed: " + newValue.getLabel());
			model.setDemandTemperature(newValue);
		}
	}

	private final Outlet model;

	// Widgets
	private final DemandFlow demandFlow;
	private final ActualFlow actualFlow;
	private final DemandTemperature demandTemperature;

	private final JLabel statusLabel;
	private final JProgressBar waitingTimePercent;

	// Listeners
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

					switch ((OutletImpl.Attribute) event.getAttribute()) {
					case DEMAND_FLOW:
						demandFlow.setSelection((Flow) event.getNewValue());
						break;
					case DEMAND_TEMPERATURE:
						demandTemperature.setSelection((Temperature) event.getNewValue());
						break;
					case DEMAND_ENABLEMENT:
						setDemandEnablement((DemandEnablement) event.getNewValue());
						break;
					case STATUS:
						setStatus((Status) event.getNewValue());
						break;
					case WAITING_TIME_PERCENT:
						setWaitingTimePercent((int) event.getNewValue());
						break;
					case ACTUAL_FLOW:
						actualFlow.setSelection((Flow) event.getNewValue());
						break;
					case NAME:
						// cannot change
					default:
						throw new IllegalArgumentException(event.getAttribute().id());
					}
				}
			});
		}
	};

	/**
	 * 
	 * @param model
	 *            cannot be {@code}
	 */
	public OutletUI(final OutletImpl model) {
		assert model != null;
		this.model = model;
		model.addModelListener(modelListener);
		this.setName(model.getLabel());

		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		GridBagLayout gbl = new GridBagLayout();
		gbl.columnWidths = new int[] { 0, 0, 0, 0 };
		gbl.columnWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
		gbl.rowHeights = new int[] { 0, 0, 0, 0 };
		gbl.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
		setLayout(gbl);

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

		// Demand Flow
		demandFlow = new DemandFlow();
		demandFlow.setEnabled(false);
		add(demandFlow, createEnumConstraints(0, 1));

		// Demand Temperature
		demandTemperature = new DemandTemperature();
		demandTemperature.setEnabled(false);
		add(demandTemperature, createEnumConstraints(1, 1));

		// Actual Flow
		actualFlow = new ActualFlow();
		actualFlow.setEnabled(false);
		add(actualFlow, createEnumConstraints(2, 1));

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
		gbc_status.gridx = 0;
		gbc_status.gridy = 2;
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

		setFocusable(false);
		updateFromModel(model);
	}

	public Outlet getModel() {
		return model;
	}

	private GridBagConstraints createEnumConstraints(int x, int y) {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.gridx = x;
		gbc.gridy = y;
		return gbc;
	}

	private void updateFromModel(Outlet model) {
		setStatus(model.getStatus());
		demandFlow.setSelection(model.getDemandFlow());
		demandTemperature.setSelection(model.getDemandTemperature());
	}

	protected void setDemandEnablement(DemandEnablement enablement) {
		switch (enablement) {
		case OFF:
			demandFlow.setEnabled(false);
			demandTemperature.setEnabled(false);
			break;
		case DOWN:
			demandFlow.setEnabled(true); // disable all
			// enable only those lower than the current demand:
			List<Flow> flows = new ArrayList<Flow>();
			for (Flow flow : Flow.values()) {
				if (flow.greaterThan(model.getDemandFlow())) {
					flows.add(flow);
				}
			}
			demandFlow.setEnabled(false, flows.toArray(new Flow[] {}));
			//
			demandTemperature.setEnabled(true); // disable all
			// enable only those lower than the current demand:
			List<Temperature> temperatures = new ArrayList<Temperature>();
			for (Temperature temp : Temperature.values()) {
				if (temp.greaterThan(model.getDemandTemperature())) {
					temperatures.add(temp);
				}
			}
			demandTemperature.setEnabled(false, temperatures.toArray(new Temperature[] {})); // enable all
			break;
		case UP_DOWN:
			demandFlow.setEnabled(true);
			demandTemperature.setEnabled(true);
			break;
		default:
			throw new IllegalArgumentException(enablement.toString());

		}
	}

	private void setStatus(Status status) {
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

	private void setWaitingTimePercent(int percent) {
		assert percent >= 0 && percent <= 100;
		waitingTimePercent.setValue(percent);
	}

	private void info(String msg) {
		LOG.info("Outlet " + getName() + ", " + msg);
	}
}
