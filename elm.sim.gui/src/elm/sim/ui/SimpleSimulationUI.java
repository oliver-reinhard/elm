package elm.sim.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import elm.sim.metamodel.SimModelEvent;
import elm.sim.metamodel.SimModelListener;
import elm.sim.model.SimpleScheduler;
import elm.sim.model.SimStatus;
import elm.sim.model.Temperature;
import elm.sim.model.impl.TapPointImpl;
import elm.sim.model.impl.SimpleSchedulerImpl;

@SuppressWarnings("serial")
public class SimpleSimulationUI extends JFrame {
	
	private AbstractTapPointUI tapPoint_1;
	private AbstractTapPointUI tapPoint_2;
	private AbstractTapPointUI tapPoint_3;
	private AbstractTapPointUI tapPoint_4;
	private SimpleSchedulerUI scheduler;

	public SimpleSimulationUI() {
		setTitle("Dusche");
		setSize(800, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		panel.setLayout(gbl);
		
		
		scheduler = new SimpleSchedulerUI(new SimpleSchedulerImpl());
		GridBagConstraints gbc_scheduler = new GridBagConstraints();
		gbc_scheduler.insets = new Insets(5, 5, 5, 5);
		gbc_scheduler.gridheight = 2;
		gbc_scheduler.gridx = 0;
		gbc_scheduler.gridy = 0;
		panel.add(scheduler, gbc_scheduler);

		tapPoint_1 = new RealTapPointUI(new TapPointImpl("2 OG lk - Dusche", Temperature.TEMP_2));
		panel.add(tapPoint_1, createOutletConstraints(1,0));
		
		tapPoint_2 = new SimTapPointUI(new TapPointImpl("2 OG lk - Küche", Temperature.TEMP_2));
		panel.add(tapPoint_2, createOutletConstraints(2,0));

		tapPoint_3 = new SimTapPointUI(new TapPointImpl("1 OG lk - Dusche", Temperature.TEMP_2));
		panel.add(tapPoint_3, createOutletConstraints(1,1));

		tapPoint_4 = new SimTapPointUI(new TapPointImpl("1 OG lk - Küche", Temperature.TEMP_2));
		panel.add(tapPoint_4, createOutletConstraints(2,1));
		
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
				
				if (e.getAttribute() == SimpleScheduler.Attribute.STATUS) {
					tapPoint_1.getModel().setSchedulerStatus((SimStatus)e.getNewValue());
					tapPoint_2.getModel().setSchedulerStatus((SimStatus)e.getNewValue());
					tapPoint_3.getModel().setSchedulerStatus((SimStatus)e.getNewValue());
					tapPoint_4.getModel().setSchedulerStatus((SimStatus)e.getNewValue());
//					if (Status.OVERLOAD == e.getNewValue()) {
//						outlet_1.getModel().setWaitingTimePercent(25);
//						outlet_2.getModel().setWaitingTimePercent(25);
//					}
				} else if (e.getAttribute() == SimpleScheduler.Attribute.WAITING_TIME_SECONDS) {
					int waitTimePercent = (int) e.getNewValue() * 100 / SimpleSchedulerImpl.SIMULATED_WAITING_TIME_SECONDS;
					tapPoint_1.getModel().setWaitingTimePercent(waitTimePercent);
					tapPoint_2.getModel().setWaitingTimePercent(waitTimePercent); 
					tapPoint_3.getModel().setWaitingTimePercent(waitTimePercent); 
					tapPoint_4.getModel().setWaitingTimePercent(waitTimePercent);
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
				SimpleSimulationUI ex = new SimpleSimulationUI();
				ex.setVisible(true);
			}
		});
	}

}
