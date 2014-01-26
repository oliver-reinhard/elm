package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.Status;
import elm.sim.model.impl.OutletImpl;

@SuppressWarnings("serial")
public class RealOutletUI extends AbstractOutletUI {

	// Listener
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
					case REFERENCE_FLOW:
					case ACTUAL_FLOW:
					case REFERENCE_TEMPERATURE:
					case ACTUAL_TEMPERATURE:
					case SCALD_TEMPERATURE:
						// not displayed by this panel => ignore changes
						break;
					case STATUS:
						setStatus((Status) event.getNewValue());
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
	public RealOutletUI(final OutletImpl model) {
		super(model);
		model.addModelListener(modelListener);
		setFocusable(false);
	}

	@Override
	protected void addPanelContent() {
		ImageIcon deviceIcon = SimulationUtil.getIcon("clage-dlx");
		assert deviceIcon != null;

		JLabel label = new JLabel(deviceIcon);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.gridwidth = 2;
		gbc.gridx = 0;
		gbc.gridy = 1;
		add(label, gbc);
	}
}
