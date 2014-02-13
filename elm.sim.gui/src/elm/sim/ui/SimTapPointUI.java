package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.Flow;
import elm.sim.model.TapPoint;
import elm.sim.model.SimStatus;
import elm.sim.model.Temperature;
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

	class TemperaturePanel extends EnumSelectorPanel<Temperature> {

		TemperaturePanel() {
			super("Temperatur", true, Temperature.TEMP_MIN, Temperature.TEMP_1, Temperature.TEMP_2, Temperature.TEMP_3, Temperature.TEMP_4);
		}

		@Override
		protected void referenceValueChanged(Temperature newValue) {
			info("Reference temperature changed: " + newValue.getLabel());
			model.setReferenceTemperature(newValue);
		}
	}

	// Widgets
	private FlowPanel flow;
	private TemperaturePanel temperature;

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
						flow.setActual((Flow) event.getNewValue());
						break;
					case REFERENCE_TEMPERATURE:
						temperature.setReference((Temperature) event.getNewValue());
						updateReferenceTemperatureEnablement();
						break;
					case ACTUAL_TEMPERATURE:
						temperature.setActual((Temperature) event.getNewValue());
						break;
					case SCALD_TEMPERATURE:
						updateReferenceTemperatureEnablement();
						break;
					case STATUS:
						setStatus((SimStatus) event.getNewValue());
						updateReferenceTemperatureEnablement();
						break;
					case WAITING_TIME_PERCENT:
						setWaitingTimePercent((int) event.getNewValue());
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
	 *            cannot be {@code null}
	 */
	public SimTapPointUI(final TapPointImpl model) {
		super(model);
		model.addModelListener(modelListener);
		setFocusable(false);
	}

	@Override
	protected void addPanelContent() {
		// Reference Temperature and Actual Temperature
		temperature = new TemperaturePanel();
		temperature.setEnabled(false);
		add(temperature, createEnumConstraints(0, 1));

		// Reference Flow and Actual Flow
		flow = new FlowPanel();
		add(flow, createEnumConstraints(1, 1));
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
	protected void updateFromModel(TapPoint model) {
		super.updateFromModel(model);
		flow.setReference(model.getReferenceFlow());
		flow.setActual(model.getActualFlow());
		temperature.setReference(model.getReferenceTemperature());
		temperature.setActual(model.getActualTemperature());
	}

	private void updateReferenceTemperatureEnablement() {
		temperature.setEnabled(false); // disable all
		List<Temperature> toEnable = new ArrayList<Temperature>();
		if (Temperature.TEMP_MIN.lessThan(model.getScaldTemperature())) {
			// enable only those lower than the current reference:
			for (Temperature literal : temperature.getLiterals()) {
				if (literal.getDegreesCelsius() <= model.getScaldTemperature().getDegreesCelsius()) {
					toEnable.add(literal);
				}
			}
		}
		temperature.setEnabled(true, toEnable.toArray(new Temperature[] {})); // enable all
	}

	private void info(String msg) {
		LOG.info("Outlet " + getName() + ", " + msg);
	}
}
