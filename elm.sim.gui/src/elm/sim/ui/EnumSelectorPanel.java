package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EtchedBorder;

import elm.sim.metamodel.SimEnum;

/**
 * Uses an enumeration (actually a {@link SimEnum} to build one or two vertical columns of {@link JRadioButton}s.
 * 
 * @param <E>
 *            actual enumeration type
 */
@SuppressWarnings("serial")
public abstract class EnumSelectorPanel<E extends SimEnum> extends JPanel {

	private static final Logger LOG = Logger.getLogger(EnumSelectorPanel.class.getName());

	private final E[] literals;
	private final List<JRadioButton> referenceValues = new ArrayList<JRadioButton>();
	private List<JRadioButton> actualValues;

	// Listeners
	private final ActionListener referenceValueListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			E literal = null;
			int i = 0;
			for (JRadioButton button : referenceValues) {
				if (src == button) {
					literal = literals[i];
					break;
				}
				i++;
			}
			if (literal == null) {
				throw new IllegalStateException("Demand button not found.");
			}
			LOG.info("EnumPanel " + getName() + " reference changed: " + literal.getLabel());
			referenceValueChanged(literal);
		}
	};

	/**
	 * Creates a widget with only one radio-button columns for the <em>reference</em> value.
	 * 
	 * @param title
	 *            title above the <em>reference-value</em> radio-button column, cannot be {@code null} or empty
	 * @param literals
	 *            literals for which to display a radio button, cannot be {@code null} or empty
	 */
	@SafeVarargs
	public EnumSelectorPanel(String title, E... literals) {
		this(title, false, literals);
	}

	/**
	 * Creates a widget with two radio-button columns, one for the <em>reference</em> value (mandatory) and one for the <em>actual</em> value (optional).
	 * 
	 * @param title
	 *            panel title
	 * @param actualValueColumn
	 *            if {@code true} then a radio-button column is added for the actual value
	 * @param literals
	 *            literals for which to display a radio button, cannot be {@code null} or empty
	 */
	@SafeVarargs
	public EnumSelectorPanel(String title, boolean actualValueColumn, E... literals) {
		assert title != null && !title.isEmpty();
		assert literals != null && literals.length > 0;

		setName(title);
		this.literals = literals;

		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = actualValueColumn ? new int[] { 0, 0, 0 } : new int[] { 0 };
		gridBagLayout.columnWeights = actualValueColumn ? new double[] { 0, 0, Double.MIN_VALUE } : new double[] { Double.MIN_VALUE };
		gridBagLayout.rowHeights = new int[literals.length + 1];
		gridBagLayout.rowWeights = new double[literals.length + 1];
		gridBagLayout.rowWeights[literals.length] = Double.MIN_VALUE;
		setLayout(gridBagLayout);

		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		if (actualValueColumn) {
			JLabel referenceLabel = new JLabel("Soll");
			GridBagConstraints gbc_ref = new GridBagConstraints();
			gbc_ref.insets = new Insets(5, 0, 0, 0);
			gbc_ref.gridx = 0;
			gbc_ref.gridy = 0;
			add(referenceLabel, gbc_ref);

			JLabel actualLabel = new JLabel("Ist");
			GridBagConstraints gbc_actual = new GridBagConstraints();
			gbc_actual.insets = new Insets(5, 0, 0, 0);
			gbc_actual.gridx = 1;
			gbc_actual.gridy = 0;
			add(actualLabel, gbc_actual);

			actualValues = new ArrayList<JRadioButton>();
		}
		
		JLabel titleLabel = new JLabel(title);
		GridBagConstraints gbc_flow = new GridBagConstraints();
		gbc_flow.insets = new Insets(5, 0, 0, 5);
		gbc_flow.gridx = actualValueColumn ? 2 : 0;
		if (actualValueColumn) {
			gbc_flow.anchor = GridBagConstraints.WEST;
		}
		gbc_flow.gridy = 0;
		add(titleLabel, gbc_flow);

		for (E literal : literals) {
			addRadioButton(literal, actualValueColumn, literal == literals[literals.length-1]);
		}

		ButtonGroup refGroup = new ButtonGroup();
		for (JRadioButton button : referenceValues) {
			refGroup.add(button);
		}

		if (actualValueColumn) {
			ButtonGroup actualGroup = new ButtonGroup();
			for (JRadioButton button : actualValues) {
				actualGroup.add(button);
			}
		}
	}

	public E[] getLiterals() {
		return literals;
	}

	/**
	 * Invoked when a radio button is chosen by the user
	 * 
	 * @param newValue
	 *            the chosen enum value
	 */
	abstract protected void referenceValueChanged(E newValue);

	public void setReference(E value) {
		int index = checkLiteral(value); // throws Exception
		referenceValues.get(index).setSelected(true);
	}

	/**
	 * This method must only be invoked if actual values are being displayed.
	 * 
	 * @param value
	 *            must be one of the values passed into the constructor
	 */
	public void setActual(E value) {
		assert actualValues != null;
		int index = checkLiteral(value); // throws Exception
		actualValues.get(index).setSelected(true);
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

	/**
	 * Enables or disables all the radio buttons on the panel.
	 */
	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		for (JRadioButton button : referenceValues) {
			button.setEnabled(enabled);
		}
	}

	/**
	 * Enables or disables only the radio buttons for the given literals.
	 * 
	 * @param enabled
	 * @param literals
	 *            cannot be {@code null}
	 */
	@SuppressWarnings("unchecked")
	public void setEnabled(boolean enabled, E... literals) {
		assert literals != null;
		for (E literal : literals) {
			int index = checkLiteral(literal); // throws Exception
			referenceValues.get(index).setEnabled(enabled);
		}
	}

	protected void addRadioButton(E literal, boolean actualValueColumn, boolean isLastLine) {
		int y = referenceValues.size() + 1; // the labels occupy the first row

		JRadioButton referenceValueButton = new JRadioButton(actualValueColumn ? null : literal.getLabel());
		referenceValueButton.setFocusable(false);
		referenceValueButton.addActionListener(referenceValueListener);

		GridBagConstraints gbc_ref = new GridBagConstraints();
		if (actualValueColumn) {
			gbc_ref.insets = isLastLine ? new Insets(0, 0, 5, 0) : new Insets(0, 0, 0, 0);
			gbc_ref.anchor = GridBagConstraints.NORTH;
		} else {
			gbc_ref.insets = isLastLine ? new Insets(0, 0, 5, 5) : new Insets(0, 0, 0, 5);
			gbc_ref.anchor = GridBagConstraints.WEST;
		}
		gbc_ref.gridx = 0;
		gbc_ref.gridy = y;
		add(referenceValueButton, gbc_ref);

		referenceValues.add(referenceValueButton);

		if (actualValueColumn) {
			JRadioButton actualValueButton = new JRadioButton();
			actualValueButton.setEnabled(false);
			actualValueButton.setFocusable(false);

			GridBagConstraints gbc_actual = new GridBagConstraints();
			gbc_actual.insets = isLastLine ? new Insets(0, 0, 5, 0) : new Insets(0, 0, 0, 0);
			gbc_actual.anchor = GridBagConstraints.NORTH;
			gbc_actual.gridx = 1;
			gbc_actual.gridy = y;
			add(actualValueButton, gbc_actual);

			actualValues.add(actualValueButton);

			JLabel label = new JLabel(literal.getLabel());
			GridBagConstraints gbc_label = new GridBagConstraints();
			gbc_label.insets = isLastLine ? new Insets(4, 0, 5, 5) : new Insets(4, 0, 0, 5);
			gbc_label.anchor = GridBagConstraints.NORTHWEST;
			gbc_label.gridx = 2;
			gbc_label.gridy = y;
			add(label, gbc_label);
		}
	}
}
