package elm.sim.model;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EtchedBorder;

import elm.sim.metamodel.SimEnum;

public abstract class EnumPanel<E extends SimEnum> extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(EnumPanel.class.getName());
	
	{ LOG.setLevel(Level.WARNING); }

	private final E[] literals;
	private final List<JRadioButton> buttons = new ArrayList<JRadioButton>();

	// Listeners
	private final ActionListener buttonListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			E literal = null;
			int i = 0;
			for (JRadioButton button : buttons) {
				if (src == button) {
					literal = literals[i];
					break;
				}
				i++;
			}
			if (literal == null) {
				throw new IllegalStateException("Source button not found.");
			}
			LOG.info("EnumPanel " + getName() + " selection changed: " + literal.getLabel());
			selectionChanged(literal);
		}
	};

	/**
	 * Create the panel.
	 */
	@SuppressWarnings("unchecked")
	public EnumPanel(String title, E... literals) {
		assert title != null && !title.isEmpty();
		assert literals.length > 0;

		setName(title);
		this.literals = literals;

		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0 };
		gridBagLayout.columnWeights = new double[] { Double.MIN_VALUE };
		gridBagLayout.rowHeights = new int[literals.length + 1];
		gridBagLayout.rowWeights = new double[literals.length + 1];
		gridBagLayout.rowWeights[literals.length] = Double.MIN_VALUE;
		setLayout(gridBagLayout);

		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		JLabel titleLabel = new JLabel(title);
		GridBagConstraints gbc_flow = new GridBagConstraints();
		gbc_flow.insets = new Insets(0, 0, 5, 5);
		gbc_flow.gridx = 0;
		gbc_flow.gridy = 0;
		add(titleLabel, gbc_flow);

		ButtonGroup group = new ButtonGroup();
		for (E literal : literals) {
			JRadioButton button = addRadioButton(literal);
			group.add(button);
		}
	}

	abstract protected void selectionChanged(E newValue);

	public void setSelection(E value) {
		int index = checkLiteral(value); // throws Exception
		buttons.get(index).setSelected(true);
	}

	private int checkLiteral(E value) {
		int i = 0;
		for (E literal : literals) {
			if (value == literal) {
				return i;
			}
			i++;
		}
		throw new IllegalArgumentException("Unsupported element: " + value.toString());
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		for (JRadioButton button : buttons) {
			button.setEnabled(enabled);
		}
	}

	@SuppressWarnings("unchecked")
	public void setEnabled(boolean enabled, E... literals) {
		for (E literal : literals) {
			int index = checkLiteral(literal); // throws Exception
			buttons.get(index).setEnabled(enabled);
		}
	}

	protected JRadioButton addRadioButton(E literal) {
		JRadioButton button = new JRadioButton(literal.getLabel());
		button.setFocusable(false);
		button.addActionListener(buttonListener);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(0, 0, 5, 5);
		gbc.gridx = 0;
		gbc.gridy = buttons.size() + 1; // the label occupies the first row
		add(button, gbc);

		buttons.add(button);

		return button;
	}
}
