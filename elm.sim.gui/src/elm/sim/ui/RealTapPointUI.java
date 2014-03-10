package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.SimStatus;
import elm.sim.model.TapPoint;
import elm.sim.model.impl.TapPointImpl;

@SuppressWarnings("serial")
public class RealTapPointUI extends AbstractTapPointUI {

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

					switch ((TapPointImpl.Attribute) event.getAttribute()) {
					case STATUS:
						setStatus((SimStatus) event.getNewValue());
						break;
					case WAITING_TIME_MILLIS:
						setWaitingTimeMillis((int) event.getNewValue());
						break;
					default:
						// ignore
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
	public RealTapPointUI(final TapPoint model) {
		super(model);
		model.addModelListener(modelListener);
		setFocusable(false);
	}

	@Override
	protected void addPanelContent() {
		super.addPanelContent();
		
		ImageIcon deviceIcon = SimUtil.getIcon("clage-dlx");
		assert deviceIcon != null;

		JLabel label = new JLabel(deviceIcon);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.gridwidth = 2;
		gbc.gridx = 0;
		gbc.gridy = 2;
		add(label, gbc);
	}
	
	@Override
	protected GridBagConstraints createLabelConstraints(int x, int y) {
		GridBagConstraints gbc = super.createLabelConstraints(x, y);
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.CENTER;
		return gbc;
	}
}
