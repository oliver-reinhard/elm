package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.Outlet;
import elm.sim.model.Scheduler;
import elm.sim.model.Status;
import elm.sim.model.Temperature;

public class SimulatorUI extends JFrame {

	private static final long serialVersionUID = 1L;
	
	private OutletUI outlet_1;
	private SchedulerMonitorUI scheduler;

	public SimulatorUI() {
		setTitle("Dusche");
		setSize(500, 300);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);
		
		outlet_1 = new OutletUI(new Outlet("Dusche", Temperature.TEMP_3));

		GridBagConstraints gbc_outlet1 = new GridBagConstraints();
		gbc_outlet1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet1.gridx = 0;
		gbc_outlet1.gridy = 0;
		panel.add(outlet_1, gbc_outlet1);
		
		scheduler = new SchedulerMonitorUI(new Scheduler());
		GridBagConstraints gbc_scheduler = new GridBagConstraints();
		gbc_scheduler.insets = new Insets(5, 5, 5, 5);
		gbc_scheduler.gridx = 1;
		gbc_scheduler.gridy = 0;
		panel.add(scheduler, gbc_scheduler);
		
		getContentPane().add(panel);
		
		
		outlet_1.getModel().addModelListener(new SimModelListener() {
			
			@Override
			public void modelChanged(SimModelEvent e) {
				System.out.println("PCL: source = " + e.getSource());
				System.out.println("PCL: value = " + e.getAttribute().id() + ": " + e.getOldValue() + " --> " + e.getNewValue());
			}
		});

		scheduler.getModel().addModelListener(new SimModelListener() {
			
			@Override
			public void modelChanged(SimModelEvent e) {
				System.out.println("PCL: source = " + e.getSource());
				System.out.println("PCL: value = " + e.getAttribute().id() + ": " + e.getOldValue() + " --> " + e.getNewValue());
				
				if (e.getAttribute() == Scheduler.Attribute.STATUS) {
					outlet_1.getModel().setSchedulerStatus((Status)e.getNewValue());
					if (Status.OVERLOAD == e.getNewValue()) {
						outlet_1.getModel().setWaitingTimePercent(25);
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
