package elm.sim.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class SimulatorUI extends JFrame {

	private static final long serialVersionUID = 1L;
	
	private OutletUI outlet_1;
	private SchedulerMonitorUI scheduler;

	public SimulatorUI() {
		setTitle("Dusche");
		setSize(400, 300);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);
		
		outlet_1 = new OutletUI("Dusche");

		GridBagConstraints gbc_outlet1 = new GridBagConstraints();
		gbc_outlet1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet1.gridx = 0;
		gbc_outlet1.gridy = 0;
		panel.add(outlet_1, gbc_outlet1);
		
		scheduler = new SchedulerMonitorUI();
		GridBagConstraints gbc_scheduler = new GridBagConstraints();
		gbc_scheduler.insets = new Insets(5, 5, 5, 5);
		gbc_scheduler.gridx = 1;
		gbc_scheduler.gridy = 0;
		panel.add(scheduler, gbc_scheduler);
		
		getContentPane().add(panel);
		
		outlet_1.setModel(new OutletModel(null, Flow.HIGH, Temperature.TEMP_1, false));
		// outlet_1.setModel(new SchedulerModel(70));
		outlet_1.setModel(new SchedulerModel(Status.SATURATION));
		outlet_1.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				System.out.println("PCL: source = " + evt.getSource());
				System.out.println("PCL: value = " + evt.getPropertyName() + ": " + evt.getOldValue() + " --> " + evt.getNewValue());
			}
		});
		
		scheduler.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				System.out.println("PCL: source = " + evt.getSource());
				System.out.println("PCL: value = " + evt.getPropertyName() + ": " + evt.getOldValue() + " --> " + evt.getNewValue());
				
				if (SchedulerModel.Properties.STATUS.id().equals(evt.getPropertyName())) {
					if (Status.OVERLOAD == evt.getNewValue()) {
						outlet_1.setModel(new SchedulerModel(25));
					} else {
						outlet_1.setModel(new SchedulerModel((Status) evt.getNewValue()));
					}
				}
			}
		});
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				SimulatorUI ex = new SimulatorUI();
				ex.setVisible(true);
			}
		});
	}

}
