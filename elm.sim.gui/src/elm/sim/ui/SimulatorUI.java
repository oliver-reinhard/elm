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

@SuppressWarnings("serial")
public class SimulatorUI extends JFrame {
	
	private AbstractOutletUI outlet_1;
	private AbstractOutletUI outlet_2;
	private AbstractOutletUI outlet_3;
	private AbstractOutletUI outlet_4;
	private SchedulerMonitorUI scheduler;

	public SimulatorUI() {
		setTitle("Dusche");
		setSize(800, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);
		
		
		scheduler = new SchedulerMonitorUI(new SchedulerImpl());
		GridBagConstraints gbc_scheduler = new GridBagConstraints();
		gbc_scheduler.insets = new Insets(5, 5, 5, 5);
		gbc_scheduler.gridheight = 2;
		gbc_scheduler.gridx = 0;
		gbc_scheduler.gridy = 0;
		panel.add(scheduler, gbc_scheduler);

		outlet_1 = new RealOutletUI(new OutletImpl("2 OG lk - Dusche", Temperature.TEMP_2));
		panel.add(outlet_1, createOutletConstraints(1,0));
		
		outlet_2 = new SimOutletUI(new OutletImpl("2 OG lk - Küche", Temperature.TEMP_2));
		panel.add(outlet_2, createOutletConstraints(2,0));

		outlet_3 = new SimOutletUI(new OutletImpl("1 OG lk - Dusche", Temperature.TEMP_2));
		panel.add(outlet_3, createOutletConstraints(1,1));

		outlet_4 = new SimOutletUI(new OutletImpl("1 OG lk - Küche", Temperature.TEMP_2));
		panel.add(outlet_4, createOutletConstraints(2,1));
		
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
					outlet_3.getModel().setSchedulerStatus((Status)e.getNewValue());
					outlet_4.getModel().setSchedulerStatus((Status)e.getNewValue());
//					if (Status.OVERLOAD == e.getNewValue()) {
//						outlet_1.getModel().setWaitingTimePercent(25);
//						outlet_2.getModel().setWaitingTimePercent(25);
//					}
				} else if (e.getAttribute() == Scheduler.Attribute.WAITING_TIME_SECONDS) {
					int waitTimePercent = (int) e.getNewValue() * 100 / SchedulerImpl.SIMULATED_WAITING_TIME_SECONDS;
					outlet_1.getModel().setWaitingTimePercent(waitTimePercent);
					outlet_2.getModel().setWaitingTimePercent(waitTimePercent); 
					outlet_3.getModel().setWaitingTimePercent(waitTimePercent); 
					outlet_4.getModel().setWaitingTimePercent(waitTimePercent);
				}
			}
		});
	}

	private GridBagConstraints createOutletConstraints(int x, int y) {
		GridBagConstraints gbc_outlet_1 = new GridBagConstraints();
		gbc_outlet_1.insets = new Insets(5, 5, 5, 5);
		gbc_outlet_1.fill = GridBagConstraints.BOTH;
		gbc_outlet_1.gridx = x;
		gbc_outlet_1.gridy = y;
		return gbc_outlet_1;
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
