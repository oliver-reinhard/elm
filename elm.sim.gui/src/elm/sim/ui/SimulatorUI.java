package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.Scheduler;
import elm.sim.model.Status;
import elm.sim.model.Temperature;
import elm.sim.model.impl.OutletImpl;
import elm.sim.model.impl.SchedulerImpl;

public class SimulatorUI extends JFrame {

	private static final long serialVersionUID = 1L;
	
	private OutletUI outlet_1;
	private OutletUI outlet_2;
	private SchedulerMonitorUI scheduler;

	public SimulatorUI() {
		setTitle("Dusche");
		setSize(800, 320);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);
		
		
		scheduler = new SchedulerMonitorUI(new SchedulerImpl());
		GridBagConstraints gbc_scheduler = new GridBagConstraints();
		gbc_scheduler.insets = new Insets(5, 5, 5, 5);
		gbc_scheduler.gridx = 0;
		gbc_scheduler.gridy = 0;
		panel.add(scheduler, gbc_scheduler);

		outlet_1 = new OutletUI(new OutletImpl("2 OG lk - Dusche", Temperature.TEMP_3));
		GridBagConstraints gbc_outlet_1 = new GridBagConstraints();
		gbc_outlet_1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet_1.gridx = 1;
		gbc_outlet_1.gridy = 0;
		panel.add(outlet_1, gbc_outlet_1);
		
		outlet_2 = new OutletUI(new OutletImpl("2 OG lk - Küche", Temperature.TEMP_3));
		GridBagConstraints gbc_outlet_2 = new GridBagConstraints();
		gbc_outlet_2.insets = new Insets(5, 5, 5, 5);
		gbc_outlet_2.gridx = 2;
		gbc_outlet_2.gridy = 0;
		panel.add(outlet_2, gbc_outlet_2);
		
		getContentPane().add(panel);
		
		
//		outlet_1.getModel().addModelListener(new SimModelListener() {
//			
//			@Override
//			public void modelChanged(SimModelEvent e) {
//				System.out.println("PCL: source = " + e.getSource());
//				System.out.println("PCL: value = " + e.getAttribute().id() + ": " + e.getOldValue() + " --> " + e.getNewValue());
//			}
//		});

		scheduler.getModel().addModelListener(new SimModelListener() {
			
			@Override
			public void modelChanged(SimModelEvent e) {
//				System.out.println("PCL: source = " + e.getSource());
//				System.out.println("PCL: value = " + e.getAttribute().id() + ": " + e.getOldValue() + " --> " + e.getNewValue());
				
				if (e.getAttribute() == Scheduler.Attribute.STATUS) {
					outlet_1.getModel().setSchedulerStatus((Status)e.getNewValue());
					outlet_2.getModel().setSchedulerStatus((Status)e.getNewValue());
//					if (Status.OVERLOAD == e.getNewValue()) {
//						outlet_1.getModel().setWaitingTimePercent(25);
//						outlet_2.getModel().setWaitingTimePercent(25);
//					}
				} else if (e.getAttribute() == Scheduler.Attribute.WAITING_TIME_SECONDS) {
					int waitTimePercent = (int) e.getNewValue() * 100 / 6;
					outlet_1.getModel().setWaitingTimePercent(waitTimePercent);
					outlet_2.getModel().setWaitingTimePercent(waitTimePercent); 
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
