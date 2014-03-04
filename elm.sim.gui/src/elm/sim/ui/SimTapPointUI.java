package elm.sim.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.Flow;
import elm.sim.model.HotWaterTemperature;
import elm.sim.model.SimStatus;
import elm.sim.model.TapPoint;
import elm.sim.model.impl.TapPointImpl;

@SuppressWarnings("serial")
public class SimTapPointUI extends AbstractTapPointUI {

	private static final Logger LOG = Logger.getLogger(SimTapPointUI.class.getName());

	class FlowPanel extends EnumSelectorPanel<Flow> {

		FlowPanel() {
			super("Menge", true, Flow.NONE, Flow.MIN, Flow.MEDIUM, Flow.MAX);
		}

		@Override
		protected void referenceValueChanged(Flow newValue) {
			info("Reference flow changed: " + newValue.getLabel());
			model.setReferenceFlow(newValue);
		}
	}

	class TemperaturePanel extends EnumSelectorPanel<HotWaterTemperature> {

		TemperaturePanel() {
			super("Temperatur", true, HotWaterTemperature.TEMP_MIN_19, HotWaterTemperature.TEMP_30, HotWaterTemperature.TEMP_38, HotWaterTemperature.TEMP_42, HotWaterTemperature.TEMP_48);
		}

		@Override
		protected void referenceValueChanged(HotWaterTemperature newValue) {
			info("Reference temperature changed: " + newValue.getLabel());
			model.setReferenceTemperature(newValue);
		}
	}

	private JLabel power;
	private TemperaturePanel temperature;
	private FlowPanel flow;
	private DecimalFormat kWFormat;

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

					switch ((TapPointImpl.Attribute) event.getAttribute()) {
					case REFERENCE_FLOW:
						flow.setReference((Flow) event.getNewValue());
						updateReferenceTemperatureEnablement();
						break;
					case ACTUAL_FLOW:
						updateFromModel(); // power
						break;
					case REFERENCE_TEMPERATURE:
						temperature.setReference((HotWaterTemperature) event.getNewValue());
						updateReferenceTemperatureEnablement();
						break;
					case ACTUAL_TEMPERATURE:
						updateFromModel(); // power
						break;
					case SCALD_PROTECTION_TEMPERATURE:
						updateReferenceTemperatureEnablement();
						break;
					case STATUS:
						setStatus((SimStatus) event.getNewValue());
						updateReferenceTemperatureEnablement();
						break;
					case WAITING_TIME_PERCENT:
						setWaitingTimePercent((int) event.getNewValue());
						break;
					case INTAKE_WATER_TEMPERATURE:
						updateFromModel(); // power
						break;
					case NAME:
					case ID:
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
	 *            cannot be {@code null}
	 */
	public SimTapPointUI(final TapPoint model) {
		super(model);
		model.addModelListener(modelListener);
		setFocusable(false);
	}

	@Override
	protected void addPanelContent() {
		super.addPanelContent();
		
		power = new JLabel("Leistung");
		add (power, createLabelConstraints(1, 1));
		// Reference Temperature and Actual Temperature
		temperature = new TemperaturePanel();
		temperature.setEnabled(false);
		add(temperature, createEnumConstraints(0, 2));

		// Reference Flow and Actual Flow
		flow = new FlowPanel();
		add(flow, createEnumConstraints(1, 2));
	}

	private GridBagConstraints createEnumConstraints(int x, int y) {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.fill = GridBagConstraints.VERTICAL;
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.gridx = x;
		gbc.gridy = y;
		return gbc;
	}

	@Override
	protected void updateFromModel() {
		super.updateFromModel();
		if (kWFormat == null) {
			kWFormat = new DecimalFormat();
			kWFormat.setMinimumFractionDigits(1);
			kWFormat.setMaximumFractionDigits(1);
		}
		final int powerWatt = model.getPowerWatt();
		power.setText("P: " + kWFormat.format(powerWatt / 1000.0) + " kW");
		if (model.getActualFlow() != Flow.NONE) {
			power.setForeground(Color.red);
		} else {
			power.setForeground(Color.black);
		}
		flow.setReference(model.getReferenceFlow());
		flow.setActual(model.getActualFlow());
		temperature.setReference(model.getReferenceTemperature());
		temperature.setActual(model.getActualTemperature());
	}

	private void updateReferenceTemperatureEnablement() {
		temperature.setEnabled(false); // disable all
		List<HotWaterTemperature> toEnable = new ArrayList<HotWaterTemperature>();
		if (HotWaterTemperature.TEMP_MIN_19.lessThan(model.getScaldProtectionTemperature())) {
			// enable only those lower than the current reference:
			for (HotWaterTemperature literal : temperature.getLiterals()) {
				if (literal.getDegreesCelsius() <= model.getScaldProtectionTemperature().getDegreesCelsius()) {
					toEnable.add(literal);
				}
			}
		}
		temperature.setEnabled(true, toEnable.toArray(new HotWaterTemperature[] {})); // enable all
	}

	private void info(String msg) {
		LOG.info("Outlet " + getName() + ", " + msg);
	}
}
